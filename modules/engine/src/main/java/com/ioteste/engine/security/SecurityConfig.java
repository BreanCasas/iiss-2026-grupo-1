package com.ioteste.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            new DateTimeFormatterBuilder()
                    .appendInstant(3)
                    .toFormatter();

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper mapper,
            @Value("${IOTESTE_API_KEY}") String apiKey) throws Exception {

        if (apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "IOTESTE_API_KEY no puede estar vacía");
        }

        OncePerRequestFilter apiKeyFilter = new OncePerRequestFilter() {

            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain)
                    throws ServletException, IOException {

                String receivedKey = request.getHeader("X-API-Key");

                if (!apiKey.equals(receivedKey)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");

                    mapper.writeValue(response.getWriter(), Map.of(
                            "timestamp", TIMESTAMP_FORMAT.format(Instant.now()),
                            "status", 401,
                            "mensaje", "X-API-Key ausente o incorrecta"
                    ));
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };

        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll())
                .addFilterBefore(
                        apiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}