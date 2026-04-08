package dev.velvet.minegrafana.spring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class MicrometerConfig {

    @Value("\${minegrafana.server-id:default}")
    private lateinit var serverId: String

    @Value("\${minegrafana.server-type:paper}")
    private lateinit var serverType: String

    @Bean
    @Primary
    fun prometheusMeterRegistry(): PrometheusMeterRegistry {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        registry.config().commonTags(
            listOf(
                Tag.of("server", serverId),
                Tag.of("server_type", serverType)
            )
        )
        return registry
    }
}
