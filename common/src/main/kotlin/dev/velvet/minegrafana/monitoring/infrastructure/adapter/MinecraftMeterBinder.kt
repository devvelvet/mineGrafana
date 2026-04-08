package dev.velvet.minegrafana.monitoring.infrastructure.adapter

import dev.velvet.minegrafana.monitoring.application.service.MonitoringApplicationService
import dev.velvet.minegrafana.monitoring.domain.model.HealthGrade
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class MinecraftMeterBinder(
    private val svc: MonitoringApplicationService
) : MeterBinder {

    private val registeredWorldGauges = ConcurrentHashMap<String, Boolean>()
    private val registeredEntityTypeGauges = ConcurrentHashMap<String, Boolean>()
    private val registeredPlayerGauges = ConcurrentHashMap<String, Boolean>()
    private val registeredPluginGauges = ConcurrentHashMap<String, Boolean>()
    private val registeredThreadGauges = ConcurrentHashMap<String, Boolean>()
    private val registeredHotClassGauges = ConcurrentHashMap<String, Boolean>()
    private var registry: MeterRegistry? = null

    companion object {
        private const val MAX_PLAYERS = 200
        private const val MAX_ENTITY_TYPES = 100
        private const val MAX_HOT_CLASSES = 50
    }

    // Set-once providers — initialized by platform module during startup
    private var _pluginCpuProvider: (() -> Map<String, Double>)? = null
    private var _threadSamplesProvider: (() -> Map<String, Long>)? = null
    private var _hotClassesProvider: (() -> Map<Pair<String, String>, Long>)? = null
    private var _tickDistProvider: (() -> LongArray)? = null

    var pluginCpuProvider: (() -> Map<String, Double>)?
        get() = _pluginCpuProvider
        set(value) { check(_pluginCpuProvider == null) { "pluginCpuProvider already set" }; _pluginCpuProvider = value }
    var threadSamplesProvider: (() -> Map<String, Long>)?
        get() = _threadSamplesProvider
        set(value) { check(_threadSamplesProvider == null) { "threadSamplesProvider already set" }; _threadSamplesProvider = value }
    var hotClassesProvider: (() -> Map<Pair<String, String>, Long>)?
        get() = _hotClassesProvider
        set(value) { check(_hotClassesProvider == null) { "hotClassesProvider already set" }; _hotClassesProvider = value }
    var tickDistProvider: (() -> LongArray)?
        get() = _tickDistProvider
        set(value) { check(_tickDistProvider == null) { "tickDistProvider already set" }; _tickDistProvider = value }

    override fun bindTo(registry: MeterRegistry) {
        this.registry = registry

        // ─── TPS ───
        g(registry, "minecraft_tps_current", "Current server TPS") { svc.getLatestHealth()?.tps?.current ?: 20.0 }
        g(registry, "minecraft_tps_avg1m", "1-minute average TPS") { svc.getLatestHealth()?.tps?.avg1m ?: 20.0 }
        g(registry, "minecraft_tps_avg5m", "5-minute average TPS") { svc.getLatestHealth()?.tps?.avg5m ?: 20.0 }
        g(registry, "minecraft_tps_avg15m", "15-minute average TPS") { svc.getLatestHealth()?.tps?.avg15m ?: 20.0 }

        // ─── MSPT ───
        g(registry, "minecraft_mspt_avg", "Average MSPT") { svc.getLatestHealth()?.mspt?.avg ?: 0.0 }
        g(registry, "minecraft_mspt_min", "Minimum MSPT") { svc.getLatestHealth()?.mspt?.min ?: 0.0 }
        g(registry, "minecraft_mspt_max", "Maximum MSPT") { svc.getLatestHealth()?.mspt?.max ?: 0.0 }
        g(registry, "minecraft_mspt_p95", "95th percentile MSPT") { svc.getLatestHealth()?.mspt?.percentile95 ?: 0.0 }

        // ─── CPU ───
        g(registry, "minecraft_cpu_process", "Process CPU %") { svc.getLatestHealth()?.cpu?.process ?: 0.0 }
        g(registry, "minecraft_cpu_system", "System CPU %") { svc.getLatestHealth()?.cpu?.system ?: 0.0 }

        // ─── Memory ───
        g(registry, "minecraft_memory_used_mb", "Used heap MB") { svc.getLatestHealth()?.memory?.usedMb?.toDouble() ?: 0.0 }
        g(registry, "minecraft_memory_max_mb", "Max heap MB") { svc.getLatestHealth()?.memory?.maxMb?.toDouble() ?: 0.0 }
        g(registry, "minecraft_memory_free_percent", "Free memory %") { svc.getLatestHealth()?.memory?.freePercent ?: 100.0 }

        // ─── Players ───
        g(registry, "minecraft_players_online", "Online player count") { svc.getLatestHealth()?.playerPings?.size?.toDouble() ?: 0.0 }
        g(registry, "minecraft_players_ping_avg", "Average player ping ms") {
            val p = svc.getLatestHealth()?.playerPings; if (p.isNullOrEmpty()) 0.0 else p.map { it.pingMs }.average()
        }

        // ─── Totals ───
        g(registry, "minecraft_entities", "Total entities") { svc.getLatestHealth()?.entities?.sumOf { it.total }?.toDouble() ?: 0.0 }
        g(registry, "minecraft_chunks_loaded", "Total loaded chunks") { svc.getLatestHealth()?.chunks?.sumOf { it.loaded }?.toDouble() ?: 0.0 }
        g(registry, "minecraft_health_grade", "0=GOOD 1=WARNING 2=CRITICAL") {
            when (svc.getLatestHealth()?.grade()) { HealthGrade.GOOD -> 0.0; HealthGrade.WARNING -> 1.0; HealthGrade.CRITICAL -> 2.0; null -> -1.0 }
        }

        // ─── Redstone ───
        g(registry, "minecraft_redstone_active", "Active redstone components") { svc.getLatestHealth()?.redstone?.activeRedstone?.toDouble() ?: 0.0 }
        g(registry, "minecraft_hoppers", "Loaded hoppers") { svc.getLatestHealth()?.redstone?.hoppers?.toDouble() ?: 0.0 }
        g(registry, "minecraft_pistons", "Loaded pistons") { svc.getLatestHealth()?.redstone?.pistons?.toDouble() ?: 0.0 }

        // ─── Network ───
        g(registry, "minecraft_network_bytes_in", "Network bytes in/s") { svc.getLatestHealth()?.network?.bytesIn?.toDouble() ?: 0.0 }
        g(registry, "minecraft_network_bytes_out", "Network bytes out/s") { svc.getLatestHealth()?.network?.bytesOut?.toDouble() ?: 0.0 }

        // ─── Disk ───
        g(registry, "minecraft_disk_used_mb", "Disk used MB") { svc.getLatestHealth()?.disk?.usedMb?.toDouble() ?: 0.0 }
        g(registry, "minecraft_disk_free_mb", "Disk free MB") { svc.getLatestHealth()?.disk?.freeMb?.toDouble() ?: 0.0 }
        g(registry, "minecraft_disk_total_mb", "Disk total MB") { svc.getLatestHealth()?.disk?.totalMb?.toDouble() ?: 0.0 }
        g(registry, "minecraft_world_size_mb", "Total world folder size MB") { svc.getLatestHealth()?.disk?.worldSizeMb?.toDouble() ?: 0.0 }

        // ─── Tile Entities total ───
        g(registry, "minecraft_tile_entities", "Total tile entities") {
            svc.getLatestHealth()?.worlds?.sumOf { it.tileEntities }?.toDouble() ?: 0.0
        }

        // ─── Tick Distribution ───
        g(registry, "minecraft_tick_under5ms", "Ticks under 5ms") { tickDistProvider?.invoke()?.get(0)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under10ms", "Ticks 5-10ms") { tickDistProvider?.invoke()?.get(1)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under25ms", "Ticks 10-25ms") { tickDistProvider?.invoke()?.get(2)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_under50ms", "Ticks 25-50ms") { tickDistProvider?.invoke()?.get(3)?.toDouble() ?: 0.0 }
        g(registry, "minecraft_tick_over50ms", "Ticks over 50ms (skipped)") { tickDistProvider?.invoke()?.get(4)?.toDouble() ?: 0.0 }
    }

    /** Called periodically to register dynamic per-world, per-entity-type, per-player gauges */
    fun registerDynamicGauges() {
        val reg = registry ?: return
        val health = svc.getLatestHealth() ?: return

        // Per-world metrics
        for (ws in health.worlds) {
            if (registeredWorldGauges.putIfAbsent(ws.name, true) == null) {
                val tags = listOf(Tag.of("world", ws.name))
                Gauge.builder("minecraft_world_entities") { svc.getLatestHealth()?.worlds?.find { it.name == ws.name }?.entities?.toDouble() ?: 0.0 }
                    .tags(tags).register(reg)
                Gauge.builder("minecraft_world_chunks") { svc.getLatestHealth()?.worlds?.find { it.name == ws.name }?.chunks?.toDouble() ?: 0.0 }
                    .tags(tags).register(reg)
                Gauge.builder("minecraft_world_tile_entities") { svc.getLatestHealth()?.worlds?.find { it.name == ws.name }?.tileEntities?.toDouble() ?: 0.0 }
                    .tags(tags).register(reg)
                Gauge.builder("minecraft_world_players") { svc.getLatestHealth()?.worlds?.find { it.name == ws.name }?.players?.toDouble() ?: 0.0 }
                    .tags(tags).register(reg)
            }
        }

        // Per entity-type metrics (capped)
        if (registeredEntityTypeGauges.size < MAX_ENTITY_TYPES) {
            for (we in health.entities) {
                for ((type, _) in we.byType) {
                    if (registeredEntityTypeGauges.size >= MAX_ENTITY_TYPES) break
                    val key = "${we.worldName}:$type"
                    if (registeredEntityTypeGauges.putIfAbsent(key, true) == null) {
                        val worldName = we.worldName
                        val typeName = type
                        Gauge.builder("minecraft_entity_type_count") {
                            svc.getLatestHealth()?.entities
                                ?.find { it.worldName == worldName }?.byType?.get(typeName)?.toDouble() ?: 0.0
                        }.tags("world", worldName, "type", typeName).register(reg)
                    }
                }
            }
        }

        // Per-player ping (capped)
        if (registeredPlayerGauges.size < MAX_PLAYERS) {
            for (pp in health.playerPings) {
                if (registeredPlayerGauges.size >= MAX_PLAYERS) break
                val name = pp.playerName
                if (registeredPlayerGauges.putIfAbsent(name, true) == null) {
                    Gauge.builder("minecraft_player_ping") {
                        svc.getLatestHealth()?.playerPings?.find { it.playerName == name }?.pingMs?.toDouble() ?: 0.0
                    }.tag("player", name).register(reg)
                }
            }
        }

        // Per-plugin CPU usage
        pluginCpuProvider?.invoke()?.forEach { (plugin, _) ->
            if (registeredPluginGauges.putIfAbsent(plugin, true) == null) {
                Gauge.builder("minecraft_plugin_cpu_percent") {
                    pluginCpuProvider?.invoke()?.get(plugin) ?: 0.0
                }.tag("plugin", plugin).description("Plugin CPU sample %").register(reg)
            }
        }

        // Per-thread samples
        threadSamplesProvider?.invoke()?.forEach { (thread, _) ->
            if (registeredThreadGauges.putIfAbsent(thread, true) == null) {
                Gauge.builder("minecraft_thread_samples") {
                    threadSamplesProvider?.invoke()?.get(thread)?.toDouble() ?: 0.0
                }.tag("thread", thread).description("Thread sample count").register(reg)
            }
        }

        // Hot classes per plugin (capped)
        hotClassesProvider?.invoke()?.entries?.take(MAX_HOT_CLASSES)?.forEach { (key, _) ->
            val (plugin, className) = key
            val gKey = "$plugin:$className"
            if (registeredHotClassGauges.size < MAX_HOT_CLASSES && registeredHotClassGauges.putIfAbsent(gKey, true) == null) {
                Gauge.builder("minecraft_plugin_hotclass_samples") {
                    hotClassesProvider?.invoke()?.get(plugin to className)?.toDouble() ?: 0.0
                }.tag("plugin", plugin).tag("class", className)
                    .description("Hot class sample count per plugin").register(reg)
            }
        }
    }

    private fun g(registry: MeterRegistry, name: String, desc: String, fn: () -> Double) {
        Gauge.builder(name, fn).description(desc).register(registry)
    }
}
