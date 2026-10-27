package com.ioteste.engine.integration;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.ioteste.engine.control.ControlService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Ejecuta HeatingEngine.tick() en bucle.
 *
 * La espera hasta el próximo ciclo la calcula el engine (nextDelay) cada
 * vez, porque depende del reloj virtual: así se despierta justo en el
 * cambio de franja horaria aunque el tiempo esté acelerado.
 *
 * Empieza cuando la aplicación está lista, sin una espera inicial fija.
 *
 * Cuando el control se inicia, se resincroniza o se detiene, el ciclo se
 * despierta de inmediato: la espera que estaba programada se calculó con
 * el estado anterior y, con tiempo acelerado, podría ser demasiado larga.
 */
@Component
public class TickLoop {

    private static final Logger log =
            LoggerFactory.getLogger(TickLoop.class);

    private final TaskScheduler scheduler;
    private final HeatingEngine engine;
    private final Clock clock;

    // Cada ejecución programada lleva el número de "generación" con que se
    // creó. Al despertar el ciclo se pasa a una generación nueva y las
    // ejecuciones viejas se descartan, para no tener dos cadenas de ciclos.
    private final AtomicInteger generation = new AtomicInteger();

    private volatile boolean active;
    private volatile ScheduledFuture<?> next;

    @Autowired
    public TickLoop(
            TaskScheduler scheduler,
            HeatingEngine engine,
            ControlService control) {

        this(scheduler, engine, control, Clock.systemUTC());
    }

    TickLoop(
            TaskScheduler scheduler,
            HeatingEngine engine,
            ControlService control,
            Clock clock) {

        this.scheduler = scheduler;
        this.engine = engine;
        this.clock = clock;

        control.onChange(this::wakeUp);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        active = true;
        wakeUp();
        log.info("Ciclo de control iniciado");
    }

    /** Programa un ciclo inmediato y descarta el que estaba esperando. */
    void wakeUp() {
        if (!active) {
            return;
        }

        int current = generation.incrementAndGet();

        ScheduledFuture<?> previous = next;

        if (previous != null) {
            previous.cancel(false);
        }

        next = scheduler.schedule(
                () -> runOnce(current),
                clock.instant()
        );
    }

    @PreDestroy
    public void stop() {
        active = false;

        ScheduledFuture<?> pending = next;

        if (pending != null) {
            pending.cancel(false);
        }
    }

    void runOnce(int runGeneration) {
        if (!active || runGeneration != generation.get()) {
            return;
        }

        try {
            engine.tick();
        } catch (Throwable failure) {
            // tick() ya captura sus errores; esto protege el bucle
            // de cualquier fallo inesperado.
            log.error(
                    "Fallo inesperado en el ciclo de control ({}).",
                    failure.getClass().getSimpleName()
            );
        } finally {
            scheduleNext(runGeneration);
        }
    }

    private void scheduleNext(int runGeneration) {
        // Si mientras tanto alguien despertó el ciclo, ya hay otro
        // programado con una generación más nueva.
        if (!active || runGeneration != generation.get()) {
            return;
        }

        Duration delay = engine.nextDelay();

        next = scheduler.schedule(
                () -> runOnce(runGeneration),
                clock.instant().plus(delay)
        );
    }
}
