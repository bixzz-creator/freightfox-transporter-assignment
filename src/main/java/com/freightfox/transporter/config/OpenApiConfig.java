package com.freightfox.transporter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger specification configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transporter Assignment on Lanes API")
                        .version("1.0.0")
                        .description("""
                                ## FreightFox Transporter Assignment Service
                                
                                This REST API solves the **Transporter Assignment on Lanes** problem across India using \
                                **Bitmask Dynamic Programming** with a documented lexicographic objective.
                                
                                ### Business Scenario
                                A logistics enterprise manages transportation lanes between Indian cities and receives \
                                price quotations from multiple transporters. Transporters may not quote for every lane.
                                
                                ### Strict Lexicographic Objective
                                1. **Maximize Distinct Transporter Usage**: Utilize the maximum number of distinct transporters \
                                   actually assigned to at least one lane, up to `maxTransporters`.
                                2. **Minimize Total Cost**: Among solutions that achieve that maximum transporter count, \
                                   select the one with the lowest total freight cost.
                                3. **Deterministic Tie-Breaking**:
                                   - Prefer the solution whose sorted selected transporter ID list is lexicographically smaller.
                                   - If still tied, compare lane assignments in lane-index order (prefer lower transporter ID).
                                
                                ### Input Replacement Behavior
                                The `POST /api/v1/transporters/input` endpoint implements an **atomic full replacement**:
                                - The entire payload is pre-validated (no duplicates, no unknown lane references, positive quotes).
                                - Existing data is removed and new data inserted within a single transactional boundary (`@Transactional`).
                                - Any validation failure or database error triggers automatic rollback, preserving previous data.
                                """)
                        .contact(new Contact()
                                .name("FreightFox Engineering Assessment")
                                .url("https://github.com/freightfox"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Default Server")
                ));
    }
}
