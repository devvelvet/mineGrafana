package dev.velvet.minegrafana.velocity.metrics

import com.velocitypowered.api.proxy.ProxyServer
import dev.velvet.minegrafana.monitoring.domain.model.*
import dev.velvet.minegrafana.monitoring.domain.service.MetricsProvider
import dev.velvet.minegrafana.shared.model.PlayerId
import java.lang.management.ManagementFactory

/**
 * Velocity proxy specific metrics collector.
 * TPS/MSPT/Entity/Chunk/Redstone do not exist on a proxy, so default values are returned.
 */
class VelocityMetricsProvider(private val server: ProxyServer) : MetricsProvider {

    override fun collectTps(): TpsSnapshot = TpsSnapshot(20.0, 20.0, 20.0, 20.0)
    override fun collectMspt(): MsptSnapshot = MsptSnapshot(0.0, 0.0, 0.0, 0.0)

    override fun collectCpu(): CpuSnapshot {
        val os = ManagementFactory.getOperatingSystemMXBean()
        var process = -1.0; var system = -1.0
        if (os is com.sun.management.OperatingSystemMXBean) {
            process = os.processCpuLoad * 100.0
            system = os.cpuLoad * 100.0
        }
        return CpuSnapshot(Math.round(process * 100.0) / 100.0, Math.round(system * 100.0) / 100.0)
    }

    override fun collectMemory(): MemorySnapshot {
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val usedMb = heap.used / (1024 * 1024)
        val maxMb = heap.max / (1024 * 1024)
        val free = if (maxMb > 0) ((maxMb - usedMb).toDouble() / maxMb * 100.0) else 0.0
        return MemorySnapshot(usedMb, maxMb, Math.round(free * 100.0) / 100.0)
    }

    override fun collectGc(): GcSnapshot {
        return GcSnapshot(ManagementFactory.getGarbageCollectorMXBeans().map {
            GcCollectorInfo(it.name, it.collectionCount, it.collectionTime)
        })
    }

    override fun collectEntities(): List<WorldEntityCount> = emptyList()
    override fun collectChunks(): List<WorldChunkCount> = emptyList()

    override fun collectPlayerPings(): List<PlayerPing> {
        return server.allPlayers.map {
            PlayerPing(PlayerId(it.uniqueId), it.username, it.ping.toInt())
        }
    }

    /** Velocity specific: per-backend-server info */
    fun collectServerStats(): List<BackendServerStats> {
        return server.allServers.map { registered ->
            val info = registered.serverInfo
            val players = registered.playersConnected.size
            BackendServerStats(
                name = info.name,
                address = "${info.address.hostString}:${info.address.port}",
                playerCount = players,
                online = players > 0 || registered.playersConnected.isNotEmpty()
            )
        }
    }

    fun getTotalPlayers(): Int = server.playerCount
    fun getServerCount(): Int = server.allServers.size
}

data class BackendServerStats(
    val name: String,
    val address: String,
    val playerCount: Int,
    val online: Boolean
)
