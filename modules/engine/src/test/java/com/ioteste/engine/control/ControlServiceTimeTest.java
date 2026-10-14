package com.ioteste.engine.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ioteste.core.PeakSchedule;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlServiceTimeTest {

    @Test
    void evaluatesPeakUsingRequestedLocalHour() throws Exception {
        ControlService service = new ControlService();

        ControlCommand command = readCommand(
                "2026-10-05T17:00:00-03:00"
        );

        ControlStatus status = service.command(command);

        LocalDateTime localTime = LocalDateTime.ofInstant(
                status.fechaHora(),
                service.timeOffset()
        );

        assertEquals(ZoneOffset.ofHours(-3), service.timeOffset());
        assertEquals(17, localTime.getHour());
        assertTrue(schedule().isPeak(localTime));
    }

    @Test
    void resynchronizationReplacesPreviousOffset() throws Exception {
        ControlService service = new ControlService();

        service.command(readCommand(
                "2026-10-05T17:00:00-03:00"
        ));

        ControlStatus status = service.command(readCommand(
                "2026-10-05T23:00:00+02:00"
        ));

        LocalDateTime localTime = LocalDateTime.ofInstant(
                status.fechaHora(),
                service.timeOffset()
        );

        assertEquals(ZoneOffset.ofHours(2), service.timeOffset());
        assertEquals(23, localTime.getHour());
        assertFalse(schedule().isPeak(localTime));
    }

    private ControlCommand readCommand(String dateTime) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        return mapper.readValue(
                """
                {
                  "accion": "INICIAR",
                  "fechaHora": "%s",
                  "factorTiempo": 1
                }
                """.formatted(dateTime),
                ControlCommand.class
        );
    }

    private PeakSchedule schedule() {
        return new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );
    }
}