package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.MemorySnapshot
import java.lang.management.ManagementFactory

object PaperMemoryProvider {

    fun collect(): MemorySnapshot {
        val memoryBean = ManagementFactory.getMemoryMXBean()
        val heapUsage = memoryBean.heapMemoryUsage

        val usedMb = heapUsage.used / (1024 * 1024)
        val maxMb = heapUsage.max / (1024 * 1024)
        val freePercent = if (maxMb > 0) {
            ((maxMb - usedMb).toDouble() / maxMb * 100.0)
        } else 0.0

        return MemorySnapshot(
            usedMb = usedMb,
            maxMb = maxMb,
            freePercent = Math.round(freePercent * 100.0) / 100.0
        )
    }
}
