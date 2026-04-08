package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.TpsSnapshot
import org.bukkit.Bukkit

object PaperTpsProvider {

    fun collect(): TpsSnapshot {
        val tps = Bukkit.getTPS() // [1m, 5m, 15m]
        return TpsSnapshot(
            current = tps[0].coerceIn(0.0, 20.0),
            avg1m = tps[0].coerceIn(0.0, 20.0),
            avg5m = tps[1].coerceIn(0.0, 20.0),
            avg15m = tps[2].coerceIn(0.0, 20.0)
        )
    }
}
