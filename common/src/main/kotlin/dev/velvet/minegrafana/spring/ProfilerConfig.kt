package dev.velvet.minegrafana.spring

import dev.velvet.minegrafana.monitoring.domain.service.ProfilerEngine
import dev.velvet.minegrafana.monitoring.infrastructure.profiler.AsyncProfilerAdapter
import dev.velvet.minegrafana.monitoring.infrastructure.profiler.JfrProfilerAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
class ProfilerConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun profilerEngine(
        @Value("\${minegrafana.data-folder:./plugins/mineGrafana}") dataFolder: String
    ): ProfilerEngine {
        val profilerDir = Path.of(dataFolder, "profiler")
        val asyncAdapter = AsyncProfilerAdapter(profilerDir)
        return if (asyncAdapter.isAvailable()) {
            logger.info("Using async-profiler engine")
            asyncAdapter
        } else {
            logger.info("Using JFR profiler engine (fallback)")
            JfrProfilerAdapter(profilerDir)
        }
    }
}
