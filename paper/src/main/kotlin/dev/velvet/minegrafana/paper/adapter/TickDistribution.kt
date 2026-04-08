package dev.velvet.minegrafana.paper.adapter

import java.util.concurrent.atomic.AtomicLong

/**
 * Tick time distribution histogram.
 * Counts how many ms each tick took, grouped by bucket.
 */
class TickDistribution {
    val under5ms = AtomicLong()    // < 5ms (excellent)
    val under10ms = AtomicLong()   // 5-10ms (good)
    val under25ms = AtomicLong()   // 10-25ms (normal)
    val under50ms = AtomicLong()   // 25-50ms (warning, full tick)
    val over50ms = AtomicLong()    // > 50ms (critical, skipped tick)

    private val totalTicks = AtomicLong()

    fun record(mspt: Double) {
        totalTicks.incrementAndGet()
        when {
            mspt < 5.0 -> under5ms.incrementAndGet()
            mspt < 10.0 -> under10ms.incrementAndGet()
            mspt < 25.0 -> under25ms.incrementAndGet()
            mspt < 50.0 -> under50ms.incrementAndGet()
            else -> over50ms.incrementAndGet()
        }
    }

    fun getTotal(): Long = totalTicks.get()

    /** Periodic reset to keep only recent data */
    fun resetIfNeeded() {
        if (totalTicks.get() > 100_000) {
            under5ms.set(0)
            under10ms.set(0)
            under25ms.set(0)
            under50ms.set(0)
            over50ms.set(0)
            totalTicks.set(0)
        }
    }
}
