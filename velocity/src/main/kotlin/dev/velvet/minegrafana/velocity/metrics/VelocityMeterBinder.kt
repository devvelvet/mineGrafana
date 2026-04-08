package dev.velvet.minegrafana.velocity.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Velocity specific Prometheus metrics.
 * Unlike Paper, only proxy-level metrics are exposed.
 */
@Component
class VelocityMeterBinder : MeterBinder {

    @Volatile var metricsProvider: VelocityMetricsProvider? = null
    private var registry: MeterRegistry? = null
    private val registeredServers = ConcurrentHashMap<String, Boolean>()
    private val registeredPlayers = ConcurrentHashMap<String, Boolean>()

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry

        // ─── Proxy totals ───
        Gauge.builder("velocity_players_total") { metricsProvider?.getTotalPlayers()?.toDouble() ?: 0.0 }
            .description("Total players on proxy").register(registry)

        Gauge.builder("velocity_servers_registered") { metricsProvider?.getServerCount()?.toDouble() ?: 0.0 }
            .description("Registered backend servers").register(registry)

        // ─── CPU / Memory (proxy JVM) ───
        Gauge.builder("velocity_cpu_process") { metricsProvider?.collectCpu()?.process ?: 0.0 }
            .description("Proxy CPU %").register(registry)

        Gauge.builder("velocity_cpu_system") { metricsProvider?.collectCpu()?.system ?: 0.0 }
            .description("System CPU %").register(registry)

        Gauge.builder("velocity_memory_used_mb") { metricsProvider?.collectMemory()?.usedMb?.toDouble() ?: 0.0 }
            .description("Proxy heap used MB").register(registry)

        Gauge.builder("velocity_memory_max_mb") { metricsProvider?.collectMemory()?.maxMb?.toDouble() ?: 0.0 }
            .description("Proxy heap max MB").register(registry)

        Gauge.builder("velocity_memory_free_percent") { metricsProvider?.collectMemory()?.freePercent ?: 100.0 }
            .description("Proxy free memory %").register(registry)

        // ─── Ping average ───
        Gauge.builder("velocity_players_ping_avg") {
            val pings = metricsProvider?.collectPlayerPings()
            if (pings.isNullOrEmpty()) 0.0 else pings.map { it.pingMs }.average()
        }.description("Average player ping").register(registry)
    }

    /** Dynamic gauges: per-server, per-player */
    fun registerDynamicGauges() {
        val reg = registry ?: return
        val provider = metricsProvider ?: return

        // Per-server player count + status
        for (srv in provider.collectServerStats()) {
            if (registeredServers.putIfAbsent(srv.name, true) == null) {
                val name = srv.name
                Gauge.builder("velocity_server_players") {
                    metricsProvider?.collectServerStats()?.find { it.name == name }?.playerCount?.toDouble() ?: 0.0
                }.tag("server", name).description("Players on backend server").register(reg)

                Gauge.builder("velocity_server_online") {
                    val online = metricsProvider?.collectServerStats()?.find { it.name == name }?.online ?: false
                    if (online) 1.0 else 0.0
                }.tag("server", name).description("Backend server status (1=online)").register(reg)
            }
        }

        // Per-player ping (capped at 200 to prevent unbounded growth)
        if (registeredPlayers.size < 200) {
            for (pp in provider.collectPlayerPings()) {
                if (registeredPlayers.size >= 200) break
                val playerName = pp.playerName
                if (registeredPlayers.putIfAbsent(playerName, true) == null) {
                    Gauge.builder("velocity_player_ping") {
                        metricsProvider?.collectPlayerPings()?.find { it.playerName == playerName }?.pingMs?.toDouble() ?: 0.0
                    }.tag("player", playerName).description("Player ping ms").register(reg)
                }
            }
        }
    }
}
