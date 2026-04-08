package dev.velvet.minegrafana.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import dev.velvet.minegrafana.config.ConfigLoader
import dev.velvet.minegrafana.config.PluginConfig
import dev.velvet.minegrafana.grafana.GrafanaProvisioner
import org.slf4j.Logger
import java.lang.management.ManagementFactory
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

            val meterBinder = springBridge!!.getBean(VelocityMeterBinder::class.java)
            if (meterBinder != null) {
                meterBinder.proxyServer = server

                // Periodic cache update + dynamic gauge registration
                server.scheduler.buildTask(this@MineGrafanaVelocity, Runnable {
                    updateCache(meterBinder)
                    meterBinder.registerDynamicGauges()
                }).repeat(Duration.ofSeconds(pluginConfig.features.monitoring.collectionIntervalSeconds.toLong())).schedule()

                logger.info("Monitoring initialized. Interval: ${pluginConfig.features.monitoring.collectionIntervalSeconds}s")
            }

            if (pluginConfig.grafana.autoProvision) {
                springBridge!!.getBean(GrafanaProvisioner::class.java)?.provision(
                    pluginConfig.grafana, "http://localhost:${pluginConfig.web.port}/metrics", "velocity"
                )
            }
        }

        server.scheduler.buildTask(this, initTask).delay(Duration.ofSeconds(1)).schedule()
        logger.info("mineGrafana Velocity v1.0.0 enabled!")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        springBridge?.stop()
        logger.info("mineGrafana Velocity disabled.")
    }

    private fun updateCache(m: VelocityMeterBinder) {
        m.totalPlayers = server.playerCount
        m.serverCount = server.allServers.size
        m.pingAvg = server.allPlayers.let { if (it.isEmpty()) 0.0 else it.map { p -> p.ping.toInt() }.average() }

        val os = ManagementFactory.getOperatingSystemMXBean()
        if (os is com.sun.management.OperatingSystemMXBean) {
            m.cpuProcess = Math.round(os.processCpuLoad * 10000.0) / 100.0
            m.cpuSystem = Math.round(os.cpuLoad * 10000.0) / 100.0
        }
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        m.memUsedMb = heap.used / (1024 * 1024)
        m.memMaxMb = heap.max / (1024 * 1024)
        m.memFreePercent = if (m.memMaxMb > 0) Math.round((m.memMaxMb - m.memUsedMb).toDouble() / m.memMaxMb * 10000.0) / 100.0 else 100.0
    }
}
