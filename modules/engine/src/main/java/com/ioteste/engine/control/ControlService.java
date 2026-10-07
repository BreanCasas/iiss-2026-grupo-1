package com.ioteste.engine.control;

import com.ioteste.engine.time.VirtualClock;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ControlService {

    private VirtualClock clock;
    private Double timeFactor;
    private Instant startedAt;
    private ZoneOffset timeOffset = ZoneOffset.UTC;

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

    public synchronized ZoneOffset timeOffset() {
        return timeOffset;
    }

    public synchronized ControlStatus command(ControlCommand command) {
        if ("DETENER".equals(command.accion())) {
            stop();
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
                System::nanoTime
        );

        timeOffset = command.fechaHora().getOffset();
        timeFactor = factor;
        startedAt = Instant.now();

        return status();
    }

    public synchronized void stop() {
        clock = null;
        timeFactor = null;
        startedAt = null;
        timeOffset = ZoneOffset.UTC;
    }
}