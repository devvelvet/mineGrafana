package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.WorldChunkCount
import org.bukkit.Bukkit

object PaperChunkProvider {

    fun collect(): List<WorldChunkCount> {
        return Bukkit.getWorlds().map { world ->
            WorldChunkCount(
                worldName = world.name,
                loaded = world.loadedChunks.size
            )
        }
    }
}
