package com.ioteste.engine.api;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            new DateTimeFormatterBuilder()
                    .appendInstant(3)
                    .toFormatter();

    public record ApiError(
            String timestamp,
            int status,
            String mensaje
    ) {
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(
            ResponseStatusException exception) {

        int status = exception.getStatusCode().value();
        String message = exception.getReason() == null
                ? "La solicitud no pudo procesarse"
                : exception.getReason();

        return ResponseEntity.status(status)
                .body(error(status, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidBody(
            HttpMessageNotReadableException exception) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(
                        400,
                        "Cuerpo JSON inválido o campos con formato incorrecto"
                ));
    }

    private ApiError error(int status, String message) {
        return new ApiError(
                TIMESTAMP_FORMAT.format(Instant.now()),
                status,
                message
        );
    }
}