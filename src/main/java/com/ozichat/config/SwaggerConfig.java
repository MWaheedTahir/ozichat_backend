package com.ozichat.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /**
     * No hard-coded server URL here.
     *
     * Springdoc automatically derives the server URL from each incoming HTTP
     * request, so Swagger UI always points to the correct host — localhost
     * when opened locally, the ngrok URL when opened via ngrok, and the
     * production domain when running in AWS. Hard-coding it breaks remote
     * access because every browser would try to call http://localhost:8080,
     * which is their own machine, not the server.
     */
    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("OziChat API")
                        .description("Production-ready real-time chat application REST API")
                        .version("1.0.0")
                        .contact(new Contact().name("OziChat Team")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
