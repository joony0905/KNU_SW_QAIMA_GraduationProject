package com.qaima.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI qaimaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QAIMA API")
                        .version("v1")
                        .description("QAIMA backend API documentation"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public RouterFunction<ServerResponse> swaggerUiHtmlRedirect() {
        return RouterFunctions.route(
                GET("/swagger-ui.html"),
                request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui/index.html")).build()
        );
    }
}
