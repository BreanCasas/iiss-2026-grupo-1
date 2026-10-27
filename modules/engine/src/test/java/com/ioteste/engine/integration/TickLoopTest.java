package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ioteste.engine.control.ControlCommand;
import com.ioteste.engine.control.ControlService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class TickLoopTest {

    private static final Instant NOW =
            Instant.parse("2026-10-05T20:00:00Z");

    private TaskScheduler scheduler;
    private HeatingEngine engine;
    private ControlService control;
    private TickLoop loop;

    private final List<Runnable> tasks = new ArrayList<>();
    private final List<Instant> times = new ArrayList<>();

    @BeforeEach
    void prepare() {
        scheduler = mock(TaskScheduler.class);
        engine = mock(HeatingEngine.class);
        control = new ControlService();

        when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    tasks.add(invocation.getArgument(0));
                    times.add(invocation.getArgument(1));
                    return null;
                });

        when(engine.nextDelay()).thenReturn(Duration.ofMillis(250));

        loop = new TickLoop(
                scheduler,
                engine,
                control,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ControlCommand start() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        return mapper.readValue(
                """
                {"accion":"INICIAR",
                 "fechaHora":"2026-10-05T16:59:00-03:00",
                 "factorTiempo":60}
                """,
                ControlCommand.class
        );
    }

    @Test
    void firstCycleIsScheduledRightAwayWithoutInitialDelay() {
        loop.start();

        assertEquals(1, tasks.size());
        assertEquals(NOW, times.get(0));
    }

    @Test
    void afterEachCycleTheNextOneUsesTheDelayChosenByTheEngine() {
        loop.start();
        tasks.get(0).run();

        verify(engine, times(1)).tick();
        assertEquals(2, tasks.size());
        assertEquals(NOW.plusMillis(250), times.get(1));
    }

    @Test
    void theLoopContinuesEvenIfTickThrows() {
        doAnswer(invocation -> {
            throw new IllegalStateException("fallo inesperado");
        }).when(engine).tick();

        loop.start();
        tasks.get(0).run();

        assertEquals(2, tasks.size());
    }

    @Test
    void startingTheControlWakesTheLoopImmediately() throws Exception {
        loop.start();
        tasks.get(0).run();

        control.command(start());

        assertEquals(3, tasks.size());
        assertEquals(NOW, times.get(2));
    }

    @Test
    void stoppingTheControlWakesTheLoopImmediately() throws Exception {
        loop.start();

        control.command(new ControlCommand("DETENER", null, null));

        assertEquals(2, tasks.size());
        assertEquals(NOW, times.get(1));
    }

    @Test
    void aRunScheduledBeforeWakingUpIsDiscarded() throws Exception {
        loop.start();
        tasks.get(0).run();          // programa la ejecución 1 (vieja)

        control.command(start());    // despierta: ejecución 2 (nueva)

        tasks.get(1).run();          // la vieja no debe correr ni reprogramar
        verify(engine, times(1)).tick();
        assertEquals(3, tasks.size());

        tasks.get(2).run();          // la nueva sí
        verify(engine, times(2)).tick();
        assertEquals(4, tasks.size());
    }

    @Test
    void afterStopNothingRunsOrIsScheduled() {
        loop.start();
        loop.stop();

        tasks.get(0).run();

        verify(engine, times(0)).tick();
        assertEquals(1, tasks.size());
    }
}
