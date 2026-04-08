package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.MsptSnapshot
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * MSPT (milliseconds per tick) collector.
 * Records MSPT at every server tick end via recordTick() from ServerTickEndEvent.
 * Maintains a ring buffer of the last 1200 ticks (~1 minute).
 */
class PaperMsptProvider {

    private val tickTimes = ConcurrentLinkedDeque<Double>()
    private val maxSamples = 1200 // ~1 minute at 20 TPS

    fun recordTick(mspt: Double) {
        tickTimes.addLast(mspt)
        while (tickTimes.size > maxSamples) {
            tickTimes.pollFirst()
        }
    }

    fun collect(): MsptSnapshot {
        val samples = tickTimes.toList()
        if (samples.isEmpty()) {
            return MsptSnapshot(avg = 0.0, min = 0.0, max = 0.0, percentile95 = 0.0)
        }

        val sorted = samples.sorted()
        val avg = samples.average()
        val min = sorted.first()
        val max = sorted.last()
        val p95Index = ((sorted.size - 1) * 0.95).toInt()
        val percentile95 = sorted[p95Index]

        return MsptSnapshot(
            avg = Math.round(avg * 100.0) / 100.0,
            min = Math.round(min * 100.0) / 100.0,
            max = Math.round(max * 100.0) / 100.0,
            percentile95 = Math.round(percentile95 * 100.0) / 100.0
        )
    }
}
