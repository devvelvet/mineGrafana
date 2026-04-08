package dev.velvet.minegrafana.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Micrometer binder — reads cached metric values and exposes them to Prometheus.
 * Platform modules (Paper/Velocity) update the cache periodically via public fields.
 * No intermediate abstraction — Micrometer IS the abstraction.
 */
@Component
class MinecraftMeterBinder : MeterBinder {

    // ─── Cache: updated by platform module on main thread ───
    @Volatile var tpsCurrent = 20.0
    @Volatile var tps1m = 20.0; @Volatile var tps5m = 20.0; @Volatile var tps15m = 20.0
    @Volatile var msptAvg = 0.0; @Volatile var msptMin = 0.0; @Volatile var msptMax = 0.0; @Volatile var msptP95 = 0.0
    @Volatile var cpuProcess = 0.0; @Volatile var cpuSystem = 0.0
    @Volatile var memUsedMb = 0L; @Volatile var memMaxMb = 0L; @Volatile var memFreePercent = 100.0
    @Volatile var entitiesTotal = 0; @Volatile var chunksTotal = 0; @Volatile var tileEntitiesTotal = 0
    @Volatile var playersOnline = 0; @Volatile var pingAvg = 0.0
    @Volatile var redstoneActive = 0; @Volatile var hoppers = 0
    @Volatile var diskUsedMb = 0L; @Volatile var diskFreeMb = 0L; @Volatile var diskTotalMb = 0L; @Volatile var worldSizeMb = 0L

    // ─── Dynamic data providers (set once by platform module) ───
    var pluginCpuProvider: (() -> Map<String, Double>)? = null
    var threadSamplesProvider: (() -> Map<String, Long>)? = null
    var hotClassesProvider: (() -> Map<Pair<String, String>, Long>)? = null
    var tickDistProvider: (() -> LongArray)? = null
    var worldStatsProvider: (() -> List<WorldSnapshot>)? = null
    var entityTypesProvider: (() -> Map<Pair<String, String>, Int>)? = null
    var playerPingsProvider: (() -> Map<String, Int>)? = null

    fun setProviders(
        pluginCpu: () -> Map<String, Double>,
        threadSamples: () -> Map<String, Long>,
        hotClasses: () -> Map<Pair<String, String>, Long>,
        tickDist: () -> LongArray,
        worldStats: () -> List<WorldSnapshot> = { emptyList() },
        entityTypes: () -> Map<Pair<String, String>, Int> = { emptyMap() },
        playerPings: () -> Map<String, Int> = { emptyMap() }
    ) {
        pluginCpuProvider = pluginCpu
        threadSamplesProvider = threadSamples
        hotClassesProvider = hotClasses
        tickDistProvider = tickDist
        worldStatsProvider = worldStats
        entityTypesProvider = entityTypes
        playerPingsProvider = playerPings
    }

    private val regWorlds = ConcurrentHashMap<String, Boolean>()
    private val regEntityTypes = ConcurrentHashMap<String, Boolean>()
    private val regPlayers = ConcurrentHashMap<String, Boolean>()
    private val regPlugins = ConcurrentHashMap<String, Boolean>()
    private val regThreads = ConcurrentHashMap<String, Boolean>()
    private val regHotClasses = ConcurrentHashMap<String, Boolean>()
    private var registry: MeterRegistry? = null

    companion object {
        private const val MAX_PLAYERS = 200
        private const val MAX_ENTITY_TYPES = 100
        private const val MAX_HOT_CLASSES = 50
    }

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry
        g(registry, "minecraft_tps_current") { tpsCurrent }
        g(registry, "minecraft_tps_avg1m") { tps1m }
        g(registry, "minecraft_tps_avg5m") { tps5m }
        g(registry, "minecraft_tps_avg15m") { tps15m }
        g(registry, "minecraft_mspt_avg") { msptAvg }
        g(registry, "minecraft_mspt_min") { msptMin }
        g(registry, "minecraft_mspt_max") { msptMax }
        g(registry, "minecraft_mspt_p95") { msptP95 }
        g(registry, "minecraft_cpu_process") { cpuProcess }
        g(registry, "minecraft_cpu_system") { cpuSystem }
        g(registry, "minecraft_memory_used_mb") { memUsedMb.toDouble() }
        g(registry, "minecraft_memory_max_mb") { memMaxMb.toDouble() }
        g(registry, "minecraft_memory_free_percent") { memFreePercent }
        g(registry, "minecraft_players_online") { playersOnline.toDouble() }
        g(registry, "minecraft_players_ping_avg") { pingAvg }
        g(registry, "minecraft_entities") { entitiesTotal.toDouble() }
        g(registry, "minecraft_chunks_loaded") { chunksTotal.toDouble() }
        g(registry, "minecraft_tile_entities") { tileEntitiesTotal.toDouble() }
        g(registry, "minecraft_health_grade") {
            when { tpsCurrent < 15 || msptP95 > 50 -> 2.0; tpsCurrent < 18 || msptP95 > 40 -> 1.0; else -> 0.0 }
        }
        g(registry, "minecraft_redstone_active") { redstoneActive.toDouble() }
        g(registry, "minecraft_hoppers") { hoppers.toDouble() }
        g(registry, "minecraft_disk_used_mb") { diskUsedMb.toDouble() }
        g(registry, "minecraft_disk_free_mb") { diskFreeMb.toDouble() }
        g(registry, "minecraft_disk_total_mb") { diskTotalMb.toDouble() }
        g(registry, "minecraft_world_size_mb") { worldSizeMb.toDouble() }
        g(registry, "minecraft_tick_under5ms") { tickDistProvider?.invoke()?.getOrNull(0)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under10ms") { tickDistProvider?.invoke()?.getOrNull(1)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under25ms") { tickDistProvider?.invoke()?.getOrNull(2)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under50ms") { tickDistProvider?.invoke()?.getOrNull(3)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_over50ms") { tickDistProvider?.invoke()?.getOrNull(4)?.toDouble() ?: 0.0 }
    }

    /** Called periodically to register dynamic per-world/player/plugin gauges */
    fun registerDynamicGauges() {
        val reg = registry ?: return
        worldStatsProvider?.invoke()?.forEach { ws ->
            if (regWorlds.putIfAbsent(ws.name, true) == null) {
                val n = ws.name
                Gauge.builder("minecraft_world_entities") { worldStatsProvider?.invoke()?.find { it.name == n }?.entities?.toDouble() ?: 0.0 }.tag("world", n).register(reg)
                Gauge.builder("minecraft_world_chunks") { worldStatsProvider?.invoke()?.find { it.name == n }?.chunks?.toDouble() ?: 0.0 }.tag("world", n).register(reg)
                Gauge.builder("minecraft_world_tile_entities") { worldStatsProvider?.invoke()?.find { it.name == n }?.tileEntities?.toDouble() ?: 0.0 }.tag("world", n).register(reg)
            }
        }
        if (regEntityTypes.size < MAX_ENTITY_TYPES) entityTypesProvider?.invoke()?.forEach { (k, _) ->
            if (regEntityTypes.size >= MAX_ENTITY_TYPES) return@forEach
            val gk = "${k.first}:${k.second}"; if (regEntityTypes.putIfAbsent(gk, true) == null) {
                Gauge.builder("minecraft_entity_type_count") { entityTypesProvider?.invoke()?.get(k)?.toDouble() ?: 0.0 }.tags("world", k.first, "type", k.second).register(reg)
            }
        }
        if (regPlayers.size < MAX_PLAYERS) playerPingsProvider?.invoke()?.forEach { (name, _) ->
            if (regPlayers.size >= MAX_PLAYERS) return@forEach
            if (regPlayers.putIfAbsent(name, true) == null) Gauge.builder("minecraft_player_ping") { playerPingsProvider?.invoke()?.get(name)?.toDouble() ?: 0.0 }.tag("player", name).register(reg)
        }
        pluginCpuProvider?.invoke()?.forEach { (plugin, _) ->
            if (regPlugins.putIfAbsent(plugin, true) == null) Gauge.builder("minecraft_plugin_cpu_percent") { pluginCpuProvider?.invoke()?.get(plugin) ?: 0.0 }.tag("plugin", plugin).register(reg)
        }
        threadSamplesProvider?.invoke()?.forEach { (thread, _) ->
            if (regThreads.putIfAbsent(thread, true) == null) Gauge.builder("minecraft_thread_samples") { threadSamplesProvider?.invoke()?.get(thread)?.toDouble() ?: 0.0 }.tag("thread", thread).register(reg)
        }
        if (regHotClasses.size < MAX_HOT_CLASSES) hotClassesProvider?.invoke()?.entries?.take(MAX_HOT_CLASSES)?.forEach { (k, _) ->
            val gk = "${k.first}:${k.second}"; if (regHotClasses.size < MAX_HOT_CLASSES && regHotClasses.putIfAbsent(gk, true) == null)
                Gauge.builder("minecraft_plugin_hotclass_samples") { hotClassesProvider?.invoke()?.get(k)?.toDouble() ?: 0.0 }.tag("plugin", k.first).tag("class", k.second).register(reg)
        }
    }

    private fun g(registry: MeterRegistry, name: String, fn: () -> Double) = Gauge.builder(name, fn).register(registry)
}

data class WorldSnapshot(val name: String, val entities: Int, val chunks: Int, val tileEntities: Int)
