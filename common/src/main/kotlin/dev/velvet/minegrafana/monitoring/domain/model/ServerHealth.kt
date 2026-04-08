package dev.velvet.minegrafana.monitoring.domain.model

import dev.velvet.minegrafana.shared.model.PlayerId
import dev.velvet.minegrafana.shared.model.ServerId
import java.time.Instant

class ServerHealth private constructor(
    val serverId: ServerId,
    val tps: TpsSnapshot,
    val mspt: MsptSnapshot,
    val cpu: CpuSnapshot,
    val memory: MemorySnapshot,
    val gc: GcSnapshot,
    val entities: List<WorldEntityCount>,
    val chunks: List<WorldChunkCount>,
    val playerPings: List<PlayerPing>,
    val worlds: List<WorldStats>,
    val redstone: RedstoneStats,
    val network: NetworkStats,
    val disk: DiskStats,
    val collectedAt: Instant
) {
    fun grade(): HealthGrade = when {
        tps.current < 15.0 || mspt.percentile95 > 50.0 -> HealthGrade.CRITICAL
        tps.current < 18.0 || mspt.percentile95 > 40.0 -> HealthGrade.WARNING
        else -> HealthGrade.GOOD
    }

    companion object {
        fun collect(
            serverId: ServerId,
            tps: TpsSnapshot,
            mspt: MsptSnapshot,
            cpu: CpuSnapshot,
            memory: MemorySnapshot,
            gc: GcSnapshot,
            entities: List<WorldEntityCount>,
            chunks: List<WorldChunkCount>,
            playerPings: List<PlayerPing>,
            worlds: List<WorldStats> = emptyList(),
            redstone: RedstoneStats = RedstoneStats(),
            network: NetworkStats = NetworkStats(),
            disk: DiskStats = DiskStats()
        ): ServerHealth = ServerHealth(
            serverId, tps, mspt, cpu, memory, gc, entities, chunks, playerPings,
            worlds, redstone, network, disk, Instant.now()
        )
    }
}

enum class HealthGrade { GOOD, WARNING, CRITICAL }

data class TpsSnapshot(val current: Double, val avg1m: Double, val avg5m: Double, val avg15m: Double)
data class MsptSnapshot(val avg: Double, val min: Double, val max: Double, val percentile95: Double)
data class CpuSnapshot(val process: Double, val system: Double)
data class MemorySnapshot(val usedMb: Long, val maxMb: Long, val freePercent: Double)
data class GcSnapshot(val collectors: List<GcCollectorInfo>)
data class GcCollectorInfo(val name: String, val count: Long, val timeMs: Long)

data class WorldEntityCount(val worldName: String, val total: Int, val byType: Map<String, Int>)
data class WorldChunkCount(val worldName: String, val loaded: Int)
data class PlayerPing(val playerId: PlayerId, val playerName: String, val pingMs: Int)

// --- Additional metrics ---

data class WorldStats(
    val name: String,
    val entities: Int,
    val chunks: Int,
    val tileEntities: Int,
    val players: Int,
    val loadTimeMs: Long
)

data class RedstoneStats(
    val activeRedstone: Int = 0,
    val hoppers: Int = 0,
    val pistons: Int = 0
)

data class NetworkStats(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val packetsIn: Long = 0,
    val packetsOut: Long = 0
)

data class DiskStats(
    val usedMb: Long = 0,
    val freeMb: Long = 0,
    val totalMb: Long = 0,
    val worldSizeMb: Long = 0
)
