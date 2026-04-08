package dev.velvet.minegrafana.paper.task

import dev.velvet.minegrafana.monitoring.application.service.MonitoringApplicationService
import dev.velvet.minegrafana.monitoring.infrastructure.adapter.MinecraftMeterBinder
import dev.velvet.minegrafana.shared.model.ServerId
import org.bukkit.scheduler.BukkitRunnable
import java.util.logging.Level
import java.util.logging.Logger

class MetricsCollectionTask(
    private val monitoringService: MonitoringApplicationService,
    private val serverId: ServerId,
    private val meterBinder: MinecraftMeterBinder? = null
) : BukkitRunnable() {

    private val logger: Logger = Logger.getLogger("mineGrafana")
    private var consecutiveErrors = 0

    override fun run() {
        try {
            monitoringService.collectAndPublish(serverId)
            meterBinder?.registerDynamicGauges()
            consecutiveErrors = 0
        } catch (e: Exception) {
            consecutiveErrors++
            // Log first error and every 30th after to avoid spam
            if (consecutiveErrors == 1 || consecutiveErrors % 30 == 0) {
                logger.log(Level.WARNING, "Metrics collection failed (x$consecutiveErrors): ${e.message}", e)
            }
        }
    }
}
