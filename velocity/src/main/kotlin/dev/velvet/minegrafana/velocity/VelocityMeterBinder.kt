package dev.velvet.minegrafana.velocity

import com.velocitypowered.api.proxy.ProxyServer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Velocity Prometheus metrics — cached values updated by MineGrafanaVelocity.
 */
@Component
class VelocityMeterBinder : MeterBinder {

    @Volatile var proxyServer: ProxyServer? = null
    @Volatile var totalPlayers = 0; @Volatile var serverCount = 0; @Volatile var pingAvg = 0.0
    @Volatile var cpuProcess = 0.0; @Volatile var cpuSystem = 0.0
    @Volatile var memUsedMb = 0L; @Volatile var memMaxMb = 0L; @Volatile var memFreePercent = 100.0

    private var registry: MeterRegistry? = null
    private val regServers = ConcurrentHashMap<String, Boolean>()
    private val regPlayers = ConcurrentHashMap<String, Boolean>()

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        g(registry, "velocity_players_total") { totalPlayers.toDouble() }
        g(registry, "velocity_servers_registered") { serverCount.toDouble() }
        g(registry, "velocity_players_ping_avg") { pingAvg }
        g(registry, "velocity_cpu_process") { cpuProcess }
        g(registry, "velocity_cpu_system") { cpuSystem }
        g(registry, "velocity_memory_used_mb") { memUsedMb.toDouble() }
        g(registry, "velocity_memory_max_mb") { memMaxMb.toDouble() }
        g(registry, "velocity_memory_free_percent") { memFreePercent }
    }

    fun registerDynamicGauges() {
        val reg = registry ?: return
        val proxy = proxyServer ?: return

        for (srv in proxy.allServers) {
            val name = srv.serverInfo.name
            if (regServers.putIfAbsent(name, true) == null) {
                Gauge.builder("velocity_server_players") { proxyServer?.getServer(name)?.orElse(null)?.playersConnected?.size?.toDouble() ?: 0.0 }.tag("server", name).register(reg)
                Gauge.builder("velocity_server_online") { if ((proxyServer?.getServer(name)?.orElse(null)?.playersConnected?.size ?: 0) >= 0) 1.0 else 0.0 }.tag("server", name).register(reg)
            }
        }

        if (regPlayers.size < 200) {
            for (player in proxy.allPlayers) {
                if (regPlayers.size >= 200) break
                val name = player.username
                if (regPlayers.putIfAbsent(name, true) == null) {
                    Gauge.builder("velocity_player_ping") { proxyServer?.getPlayer(name)?.orElse(null)?.ping?.toDouble() ?: 0.0 }.tag("player", name).register(reg)
                }
            }
        }
    }

    private fun g(registry: MeterRegistry, name: String, fn: () -> Double) = Gauge.builder(name, fn).register(registry)
}
