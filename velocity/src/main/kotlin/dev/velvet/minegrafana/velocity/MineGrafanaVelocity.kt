package dev.velvet.minegrafana.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import dev.velvet.minegrafana.monitoring.application.service.MonitoringApplicationService
import dev.velvet.minegrafana.shared.config.ConfigLoader
import dev.velvet.minegrafana.shared.config.PluginConfig
import dev.velvet.minegrafana.shared.model.ServerId
import dev.velvet.minegrafana.velocity.metrics.VelocityMeterBinder
import dev.velvet.minegrafana.velocity.metrics.VelocityMetricsProvider
import dev.velvet.minegrafana.velocity.spring.VelocitySpringBridge
import org.slf4j.Logger
import java.nio.file.Path
import java.time.Duration

@Plugin(
    id = "minegrafana",
    name = "mineGrafana",
    version = "1.0.0",
    description = "Server performance monitoring with Grafana metrics integration",
    authors = ["velvet"]
)
class MineGrafanaVelocity @Inject constructor(
    val server: ProxyServer,
    val logger: Logger,
    @param:DataDirectory val dataDirectory: Path
) {
    lateinit var pluginConfig: PluginConfig
        private set

    private var springBridge: VelocitySpringBridge? = null

    @Subscribe
    fun onProxyInit(event: ProxyInitializeEvent) {
        try {
            pluginConfig = ConfigLoader.loadConfig(dataDirectory)
            logger.info("Configuration loaded. Type: ${pluginConfig.serverType}, Port: ${pluginConfig.web.port}")
        } catch (e: Exception) {
            logger.error("Failed to load configuration", e)
            return
        }

        try {
            springBridge = VelocitySpringBridge(this)
            springBridge!!.start(pluginConfig)
            logger.info("Spring Boot starting on port ${pluginConfig.web.port}...")
        } catch (e: Exception) {
            logger.error("Failed to start Spring Boot", e)
            return
        }

        val initTask = Runnable {
            if (!springBridge!!.awaitReady(30)) {
                logger.error("Spring Boot failed to start within 30 seconds!")
                return@Runnable
            }
            logger.info("Spring Boot is ready. Initializing Velocity metrics...")

            val metricsProvider = VelocityMetricsProvider(server)

            // Wire monitoring service
            val monitoringService = springBridge!!.getBean(MonitoringApplicationService::class.java)
            if (monitoringService != null) {
                monitoringService.metricsProvider = metricsProvider
                val serverId = ServerId(pluginConfig.serverId)
                val interval = pluginConfig.features.monitoring.collectionIntervalSeconds.toLong()

                server.scheduler.buildTask(this@MineGrafanaVelocity, Runnable {
                    monitoringService.collectAndPublish(serverId)
                }).repeat(Duration.ofSeconds(interval)).schedule()

                logger.info("Monitoring initialized. Interval: ${interval}s")
            }

            // Wire Velocity-specific meter binder
            val meterBinder = springBridge!!.getBean(VelocityMeterBinder::class.java)
            if (meterBinder != null) {
                meterBinder.metricsProvider = metricsProvider

                // Periodic dynamic gauge registration
                server.scheduler.buildTask(this@MineGrafanaVelocity, Runnable {
                    meterBinder.registerDynamicGauges()
                }).repeat(Duration.ofSeconds(5)).schedule()

                logger.info("Velocity meter binder initialized.")
            }

            // Grafana auto-provision
            if (pluginConfig.grafana.autoProvision) {
                val provisioner = springBridge!!.getBean(
                    dev.velvet.minegrafana.monitoring.infrastructure.grafana.GrafanaProvisioner::class.java
                )
                provisioner?.provision(pluginConfig.grafana, "http://localhost:${pluginConfig.web.port}/metrics", "velocity")
            }
        }

        server.scheduler.buildTask(this, initTask)
            .delay(Duration.ofSeconds(1))
            .schedule()

        logger.info("mineGrafana Velocity v1.0.0 enabled!")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        springBridge?.stop()
        logger.info("mineGrafana Velocity disabled.")
    }
}
