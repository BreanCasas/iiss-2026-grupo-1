package com.ioteste.engine.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduler propio con varios hilos.
 *
 * Con la configuración por defecto de Spring hay un único hilo para todas
 * las tareas programadas: el registro ante el entorno, la sincronización
 * de suscripciones MQTT y el ciclo de control se bloqueaban entre sí
 * (por ejemplo, una conexión MQTT lenta demoraba el corte de las 17:00).
 */
@Configuration
public class SchedulingConfig {

    static final int THREADS = 4;

    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(THREADS);
        scheduler.setThreadNamePrefix("ioteste-sched-");
        scheduler.setRemoveOnCancelPolicy(true);

        return scheduler;
    }
}
