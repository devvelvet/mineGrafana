package dev.velvet.minegrafana.paper.listener

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.velvet.minegrafana.paper.adapter.PaperMsptProvider
import dev.velvet.minegrafana.paper.adapter.ThreadProfiler
import dev.velvet.minegrafana.paper.adapter.TickDistribution
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class TickEventListener(
    private val msptProvider: PaperMsptProvider,
    private val threadProfiler: ThreadProfiler,
    private val tickDistribution: TickDistribution
) : Listener {

    private var sampleCounter = 0

    @EventHandler
    fun onTickEnd(event: ServerTickEndEvent) {
        val mspt = event.tickDuration
        msptProvider.recordTick(mspt)
        tickDistribution.record(mspt)

        // Sample threads every 5 ticks (~250ms) to keep overhead low
        if (++sampleCounter >= 5) {
            sampleCounter = 0
            threadProfiler.sample()
            threadProfiler.resetIfNeeded()
            tickDistribution.resetIfNeeded()
        }
    }
}
