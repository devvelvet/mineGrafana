package dev.velvet.minegrafana.spring

import io.micrometer.influx.InfluxConfig
import io.micrometer.influx.InfluxMeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConditionalOnProperty(name = ["minegrafana.metrics.influx.enabled"], havingValue = "true")
class InfluxDbConfig {

    @Value("\${minegrafana.metrics.influx.url:http://localhost:8086}")
    private lateinit var url: String

    @Value("\${minegrafana.metrics.influx.org:minecraft}")
    private lateinit var org: String

    @Value("\${minegrafana.metrics.influx.bucket:minegrafana}")
    private lateinit var bucket: String

    @Value("\${minegrafana.metrics.influx.token:}")
    private lateinit var token: String

    @Value("\${minegrafana.metrics.influx.step-seconds:10}")
    private var stepSeconds: Int = 10

    @Value("\${minegrafana.server-id:default}")
    private lateinit var serverId: String

    @Bean
    fun influxMeterRegistry(): InfluxMeterRegistry {
        val config = object : InfluxConfig {
            override fun get(key: String): String? = null
            override fun uri(): String = url
            override fun org(): String = this@InfluxDbConfig.org
            override fun bucket(): String = this@InfluxDbConfig.bucket
            override fun token(): String = this@InfluxDbConfig.token
            override fun step(): Duration = Duration.ofSeconds(stepSeconds.toLong())
            override fun autoCreateDb(): Boolean = true
        }

        val registry = InfluxMeterRegistry(config, io.micrometer.core.instrument.Clock.SYSTEM)
        registry.config().commonTags("server", serverId)
        return registry
    }
}
