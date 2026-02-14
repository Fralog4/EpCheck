package com.source.epcheck.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAPIConfig {

    @Bean
    fun epsteinLensOpenAPI(): OpenAPI {
        return OpenAPI()
                .info(
                        Info().title("EpsteinLens API")
                                .description("API for OSINT Fact-Checking Tool")
                                .version("v0.1.0-alpha")
                )
    }
}
