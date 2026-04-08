package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.GcCollectorInfo
import dev.velvet.minegrafana.monitoring.domain.model.GcSnapshot
import java.lang.management.ManagementFactory

object PaperGcProvider {

    fun collect(): GcSnapshot {
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val collectors = gcBeans.map { gc ->
            GcCollectorInfo(
                name = gc.name,
                count = gc.collectionCount,
                timeMs = gc.collectionTime
            )
        }
        return GcSnapshot(collectors)
    }
}
