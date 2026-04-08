package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.WorldEntityCount
import org.bukkit.Bukkit

object PaperEntityProvider {

    fun collect(): List<WorldEntityCount> {
        return Bukkit.getWorlds().map { world ->
            val byType = mutableMapOf<String, Int>()
            world.entities.forEach { entity ->
                val typeName = entity.type.name
                byType[typeName] = (byType[typeName] ?: 0) + 1
            }
            WorldEntityCount(
                worldName = world.name,
                total = world.entityCount,
                byType = byType
            )
        }
    }
}
