package com.ioteste.engine.control;

import com.ioteste.engine.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.ioteste.engine.api.ApiExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;



@WebMvcTest(
        controllers = ControlController.class,
        properties = "IOTESTE_API_KEY=test-key"
)
@Import({
        SecurityConfig.class,
        ControlService.class,
        ApiExceptionHandler.class
})
class ControlControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ControlService service;

    @BeforeEach
    void resetControl() {
        service.stop();
    }

    @Test
    void rejectsRequestWithoutApiKey() throws Exception {
        mvc.perform(get("/control"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsRequestWithIncorrectApiKey() throws Exception {
        mvc.perform(get("/control")
                        .header("X-API-Key", "incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void returnsStoppedStateWithCorrectApiKey() throws Exception {
        mvc.perform(get("/control")
                        .header("X-API-Key", "test-key"))
                .andExpect(jsonPath("$.fechaHora").value(nullValue()))
                .andExpect(jsonPath("$.factorTiempo").value(nullValue()))
                .andExpect(jsonPath("$.iniciadoEn").value(nullValue()));
    }

    @Test
    void startsControlWithRequestedTimeAndFactor() throws Exception {
        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accion": "INICIAR",
                              "fechaHora": "2026-10-05T16:59:00Z",
                              "factorTiempo": 60
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.factorTiempo").value(60.0))
                .andExpect(jsonPath("$.fechaHora").isString())
                .andExpect(jsonPath("$.iniciadoEn").isString());
    }

    @Test
    void stopsRunningControlAndClearsTimeFields() throws Exception {
        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accion": "INICIAR",
                              "fechaHora": "2026-10-05T16:59:00Z",
                              "factorTiempo": 60
                            }
                            """))
                .andExpect(status().isOk());

        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"accion": "DETENER"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDO"))
                .andExpect(jsonPath("$.fechaHora").value(nullValue()))
                .andExpect(jsonPath("$.factorTiempo").value(nullValue()))
                .andExpect(jsonPath("$.iniciadoEn").value(nullValue()));

        mvc.perform(get("/control")
                        .header("X-API-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDO"));
    }

    @Test
    void defaultsTimeFactorToOneWhenOmitted() throws Exception {
        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accion": "INICIAR",
                              "fechaHora": "2026-10-05T16:59:00Z"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.factorTiempo").value(1.0));
    }

    @Test
    void rejectsZeroTimeFactorWithoutStartingControl() throws Exception {
        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accion": "INICIAR",
                              "fechaHora": "2026-10-05T16:59:00Z",
                              "factorTiempo": 0
                            }
                            """))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/control")
                        .header("X-API-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDO"));
    }

    @Test
    void returnsStandardErrorWhenStartTimeIsMissing() throws Exception {
        mvc.perform(post("/control/comandos")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "accion": "INICIAR",
                              "factorTiempo": 60
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").isString());
    }
}