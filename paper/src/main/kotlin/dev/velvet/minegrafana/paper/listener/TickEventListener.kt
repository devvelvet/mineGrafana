package dev.velvet.minegrafana.paper.listener

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.velvet.minegrafana.paper.adapter.PaperMsptProvider
import dev.velvet.minegrafana.paper.adapter.TickDistribution
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class TickEventListener(
    private val msptProvider: PaperMsptProvider,
    private val tickDistribution: TickDistribution
) : Listener {

    @EventHandler
    fun onTickEnd(event: ServerTickEndEvent) {
        val mspt = event.tickDuration
        msptProvider.recordTick(mspt)
        tickDistribution.record(mspt)
        tickDistribution.resetIfNeeded()
    }
}
