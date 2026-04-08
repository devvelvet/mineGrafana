package dev.velvet.minegrafana.monitoring.application.service

import dev.velvet.minegrafana.monitoring.domain.event.HealthCollectedEvent
import dev.velvet.minegrafana.monitoring.domain.model.ServerHealth
import dev.velvet.minegrafana.monitoring.domain.service.HealthAssessor
import dev.velvet.minegrafana.monitoring.domain.service.HealthAssessment
import dev.velvet.minegrafana.monitoring.domain.service.MetricsProvider
import dev.velvet.minegrafana.shared.model.ServerId
import dev.velvet.minegrafana.spring.DomainEventPublisher
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

@Service
class MonitoringApplicationService(
    private val eventPublisher: DomainEventPublisher,
    private val healthAssessor: HealthAssessor
) {
    private val latestHealth = AtomicReference<ServerHealth?>()
    private var _metricsProvider: MetricsProvider? = null

    /** Set once during plugin initialization. Throws if already set. */
    var metricsProvider: MetricsProvider?
        get() = _metricsProvider
        set(value) {
            check(_metricsProvider == null) { "metricsProvider already initialized" }
            _metricsProvider = value
        }

    fun collectAndPublish(serverId: ServerId) {
        val provider = _metricsProvider ?: return

        val health = ServerHealth.collect(
            serverId = serverId,
            tps = provider.collectTps(),
            mspt = provider.collectMspt(),
            cpu = provider.collectCpu(),
            memory = provider.collectMemory(),
            gc = provider.collectGc(),
            entities = provider.collectEntities(),
            chunks = provider.collectChunks(),
            playerPings = provider.collectPlayerPings(),
            worlds = provider.collectWorldStats(),
            redstone = provider.collectRedstone(),
            network = provider.collectNetwork(),
            disk = provider.collectDisk()
        )

        latestHealth.set(health)
        eventPublisher.publish(HealthCollectedEvent(health))
    }

    fun getLatestHealth(): ServerHealth? = latestHealth.get()

    fun assessHealth(): HealthAssessment? {
        return latestHealth.get()?.let { healthAssessor.assess(it) }
    }
}
