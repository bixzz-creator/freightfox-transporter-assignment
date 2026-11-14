package com.freightfox.transporter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Actuator Health and OpenAPI / Swagger Integration Tests")
class ActuatorAndSwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI JSON endpoint /v3/api-docs is available and contains required schemas")
    void testOpenApiJsonLoadsSuccessfully() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", notNullValue()))
                .andExpect(jsonPath("$.info.title", is("Transporter Assignment on Lanes API")))
                .andExpect(jsonPath("$.paths['/api/v1/transporters/input'].post", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/transporters/assignment'].post", notNullValue()))
                .andExpect(jsonPath("$.components.schemas.InputRequest", notNullValue()))
                .andExpect(jsonPath("$.components.schemas.AssignmentResponse", notNullValue()))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse", notNullValue()));
    }

    @Test
    @DisplayName("Swagger UI endpoint is reachable and redirects or loads index.html")
    void testSwaggerUiIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    @DisplayName("Actuator /actuator/health endpoint is UP and includes database health")
    void testActuatorHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.components.db.status", is("UP")));
    }

    @Test
    @DisplayName("Actuator sensitive endpoints like /actuator/env are NOT exposed")
    void testActuatorSensitiveEndpointsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
