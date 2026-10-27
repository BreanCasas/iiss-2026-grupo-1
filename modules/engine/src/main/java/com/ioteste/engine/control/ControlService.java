package com.ioteste.engine.control;

import com.ioteste.engine.time.VirtualClock;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlService {

    private VirtualClock clock;
    private Double timeFactor;
    private Instant startedAt;
    /**
     * Zona horaria única del sitio. La franja de energía cara
     * (por ejemplo 17:00-23:00) siempre se evalúa en esta zona,
     * sin importar el offset con que llegue la fecha en INICIAR.
     * Uruguay: UTC-03:00 todo el año.
     */
    static final String DEFAULT_ZONE = "America/Montevideo";

    private final ZoneId zone;

    // Fuentes de tiempo inyectables: en producción son el reloj real;
    // en los tests se reemplazan para controlar el paso del tiempo.
    private final LongSupplier nanoTime;
    private final Supplier<Instant> wallClock;

    // Quienes necesitan enterarse apenas cambia el estado del control
    // (por ejemplo, el ciclo de control, para no esperar su próximo turno).
    private final List<Runnable> changeListeners =
            new CopyOnWriteArrayList<>();

    public ControlService() {
        this(
                resolveZone(System.getenv("ECOWARM_ZONE")),
                System::nanoTime,
                Instant::now
        );
    }

    ControlService(
            ZoneId zone,
            LongSupplier nanoTime,
            Supplier<Instant> wallClock) {

        this.zone = zone;
        this.nanoTime = nanoTime;
        this.wallClock = wallClock;
    }

    public synchronized ControlStatus status() {
        if (clock == null) {
            return ControlStatus.stopped();
        }

        return new ControlStatus(
                ControlStatus.State.EN_EJECUCION,
                clock.now(),
                timeFactor,
                startedAt
        );
    }

    public ZoneId zone() {
        return zone;
    }

    /** Registra una acción que se ejecuta después de INICIAR o DETENER. */
    public void onChange(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyChange() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    static ZoneId resolveZone(String configured) {
        return ZoneId.of(
                configured == null || configured.isBlank()
                        ? DEFAULT_ZONE
                        : configured.trim()
        );
    }

    public synchronized ControlStatus command(ControlCommand command) {
        if ("DETENER".equals(command.accion())) {
            stop();
            notifyChange();
            return status();
        }

        if (!"INICIAR".equals(command.accion())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Acción desconocida"
            );
        }

        double factor = command.factorTiempo() == null
                ? 1.0
                : command.factorTiempo();

        if (command.fechaHora() == null
                || !Double.isFinite(factor)
                || factor <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "INICIAR requiere fechaHora y factorTiempo mayor que cero"
            );
        }

        clock = new VirtualClock(
                command.fechaHora().toInstant(),
                factor,
                nanoTime
        );

        timeFactor = factor;
        startedAt = wallClock.get();

        notifyChange();

        return status();
    }

    public synchronized void stop() {
        clock = null;
        timeFactor = null;
        startedAt = null;
    }
}