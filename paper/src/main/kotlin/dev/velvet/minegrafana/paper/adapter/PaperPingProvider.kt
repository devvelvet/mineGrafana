package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.PlayerPing
import dev.velvet.minegrafana.shared.model.PlayerId
import org.bukkit.Bukkit

object PaperPingProvider {

    fun collect(): List<PlayerPing> {
        return Bukkit.getOnlinePlayers().map { player ->
            PlayerPing(
                playerId = PlayerId(player.uniqueId),
                playerName = player.name,
                pingMs = player.ping
            )
        }
    }
}
