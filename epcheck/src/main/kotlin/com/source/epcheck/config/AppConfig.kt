package com.source.epcheck.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.resilience.annotation.EnableResilientMethods
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableResilientMethods
class AppConfig {

    @Bean
    fun epsteinLensOpenAPI(): OpenAPI {
        return OpenAPI()
                .info(
                        Info().title("EpsteinLens API")
                                .description("API for OSINT Fact-Checking Tool")
                                .version("v0.1.0-alpha")
                )
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders("*")
            }
        }
    }
}
