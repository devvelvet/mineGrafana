package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.*
import dev.velvet.minegrafana.monitoring.domain.service.MetricsProvider
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Hopper
import java.io.File

class PaperMetricsProvider(
    val msptProvider: PaperMsptProvider
) : MetricsProvider {

    override fun collectTps(): TpsSnapshot = PaperTpsProvider.collect()
    override fun collectMspt(): MsptSnapshot = msptProvider.collect()
    override fun collectCpu(): CpuSnapshot = PaperCpuProvider.collect()
    override fun collectMemory(): MemorySnapshot = PaperMemoryProvider.collect()
    override fun collectGc(): GcSnapshot = PaperGcProvider.collect()
    override fun collectEntities(): List<WorldEntityCount> = PaperEntityProvider.collect()
    override fun collectChunks(): List<WorldChunkCount> = PaperChunkProvider.collect()
    override fun collectPlayerPings(): List<PlayerPing> = PaperPingProvider.collect()

    override fun collectWorldStats(): List<WorldStats> {
        return Bukkit.getWorlds().map { world ->
            val loadedChunks = world.loadedChunks
            var tileEntityCount = 0
            for (chunk in loadedChunks) {
                tileEntityCount += chunk.tileEntities.size
            }
            WorldStats(
                name = world.name,
                entities = world.entityCount,
                chunks = loadedChunks.size,
                tileEntities = tileEntityCount,
                players = world.playerCount,
                loadTimeMs = 0
            )
        }
    }

    override fun collectRedstone(): RedstoneStats {
        var hoppers = 0
        var redstoneComponents = 0

        for (world in Bukkit.getWorlds()) {
            for (chunk in world.loadedChunks) {
                for (te in chunk.tileEntities) {
                    when {
                        te is Hopper -> hoppers++
                        // Comparators and repeaters ARE tile entities
                        te.type == Material.COMPARATOR -> redstoneComponents++
                        te.type == Material.REPEATER -> redstoneComponents++
                    }
                }
            }
        }

        // Pistons and redstone wire are NOT tile entities — cannot count from chunk.tileEntities
        return RedstoneStats(activeRedstone = redstoneComponents, hoppers = hoppers, pistons = 0)
    }

    // Network stats not reliably available from JVM — returns zeros
    override fun collectNetwork(): NetworkStats = NetworkStats()

    override fun collectDisk(): DiskStats {
        return try {
            val serverDir = Bukkit.getWorldContainer()
            val usable = serverDir.usableSpace
            val total = serverDir.totalSpace
            val used = total - usable

            var worldSize = 0L
            for (world in Bukkit.getWorlds()) {
                val worldFolder = world.worldFolder
                if (worldFolder.exists()) {
                    worldSize += folderSize(worldFolder)
                }
            }

            DiskStats(
                usedMb = used / (1024 * 1024),
                freeMb = usable / (1024 * 1024),
                totalMb = total / (1024 * 1024),
                worldSizeMb = worldSize / (1024 * 1024)
            )
        } catch (_: Exception) {
            DiskStats()
        }
    }

    private fun folderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            size += if (file.isDirectory) folderSize(file) else file.length()
        }
        return size
    }
}
