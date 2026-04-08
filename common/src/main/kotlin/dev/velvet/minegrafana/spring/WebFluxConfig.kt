package dev.velvet.minegrafana.spring

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@Configuration
class WebFluxConfig {

    @Bean
    fun metricsRouter(registry: PrometheusMeterRegistry): RouterFunction<ServerResponse> = router {
        GET("/metrics") {
            ServerResponse.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(registry.scrape())
        }
    }
}
