package dev.velvet.minegrafana.paper

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * MSPT (milliseconds per tick) collector.
 * Maintains a ring buffer of the last 1200 ticks (~1 minute).
 */
class PaperMsptProvider {

    private val tickTimes = ConcurrentLinkedDeque<Double>()
    private val maxSamples = 1200

    fun recordTick(mspt: Double) {
        tickTimes.addLast(mspt)
        while (tickTimes.size > maxSamples) tickTimes.pollFirst()
    }

    fun collect(): MsptResult {
        val samples = tickTimes.toList()
        if (samples.isEmpty()) return MsptResult(0.0, 0.0, 0.0, 0.0)
        val sorted = samples.sorted()
        return MsptResult(
            avg = Math.round(samples.average() * 100.0) / 100.0,
            min = Math.round(sorted.first() * 100.0) / 100.0,
            max = Math.round(sorted.last() * 100.0) / 100.0,
            p95 = Math.round(sorted[((sorted.size - 1) * 0.95).toInt()] * 100.0) / 100.0
        )
    }
}

data class MsptResult(val avg: Double, val min: Double, val max: Double, val p95: Double)
