package dev.velvet.minegrafana.paper.adapter

import org.bukkit.Bukkit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight thread sampler -- Spark-style per-plugin CPU usage tracking.
 */
class ThreadProfiler {

    private val pluginSamples = ConcurrentHashMap<String, AtomicLong>()
    private val classSamples = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()
    private val threadSamples = ConcurrentHashMap<String, AtomicLong>()

    // Total thread observation count (N threads per sample() call -> N increments)
    private val totalThreadObservations = AtomicLong(0)

    private val pluginPackages = ConcurrentHashMap<String, String>()

    fun init() {
        for (plugin in Bukkit.getPluginManager().plugins) {
            val mainClass = plugin.description.main
            val packagePrefix = mainClass.substringBeforeLast(".")
            pluginPackages[packagePrefix] = plugin.name
            val parts = packagePrefix.split(".")
            if (parts.size >= 3) {
                pluginPackages[parts.take(3).joinToString(".")] = plugin.name
            }
        }
    }

    fun sample() {
        val traces = Thread.getAllStackTraces()

        for ((thread, stackTrace) in traces) {
            if (stackTrace.isEmpty()) continue
            totalThreadObservations.incrementAndGet()

            val threadName = categorizeThread(thread.name)
            threadSamples.getOrPut(threadName) { AtomicLong() }.incrementAndGet()

            for (frame in stackTrace) {
                val className = frame.className
                val plugin = identifyPlugin(className)
                if (plugin != null) {
                    pluginSamples.getOrPut(plugin) { AtomicLong() }.incrementAndGet()
                    val classMap = classSamples.getOrPut(plugin) { ConcurrentHashMap() }
                    val shortClass = className.substringAfterLast(".")
                    classMap.getOrPut(shortClass) { AtomicLong() }.incrementAndGet()
                    break
                }
            }
        }
    }

    /** Per-plugin CPU usage percentage (0.0 ~ 100.0) -- ratio against total thread observations */
    fun getPluginCpuPercent(): Map<String, Double> {
        val total = totalThreadObservations.get().coerceAtLeast(1)
        return pluginSamples.mapValues { (_, count) ->
            Math.round(count.get().toDouble() / total * 100.0 * 100) / 100.0
        }
    }

    fun getHotClasses(plugin: String, topN: Int = 5): Map<String, Long> {
        return classSamples[plugin]?.entries
            ?.sortedByDescending { it.value.get() }
            ?.take(topN)
            ?.associate { it.key to it.value.get() } ?: emptyMap()
    }

    fun getThreadSamples(): Map<String, Long> {
        return threadSamples.mapValues { it.value.get() }
    }

    fun getAllHotClasses(): Map<Pair<String, String>, Long> {
        val result = mutableMapOf<Pair<String, String>, Long>()
        for ((plugin, classMap) in classSamples) {
            for ((className, count) in classMap) {
                result[plugin to className] = count.get()
            }
        }
        return result
    }

    fun getTotalObservations(): Long = totalThreadObservations.get()

    fun resetIfNeeded() {
        if (totalThreadObservations.get() > 100_000) {
            pluginSamples.clear()
            classSamples.clear()
            threadSamples.clear()
            totalThreadObservations.set(0)
        }
    }

    private fun identifyPlugin(className: String): String? {
        for ((prefix, name) in pluginPackages) {
            if (className.startsWith(prefix)) return name
        }
        return null
    }

    private fun categorizeThread(name: String): String = when {
        name == "Server thread" -> "Server thread"
        name.startsWith("Craft Async") || name.startsWith("Craft Scheduler") -> "Bukkit Async"
        name.startsWith("Netty") -> "Netty IO"
        name.startsWith("Worker-") || name.startsWith("Paper-Worker") -> "Paper Worker"
        name.startsWith("Chunk") -> "Chunk System"
        name.startsWith("minegrafana") -> "mineGrafana"
        name.startsWith("parallel-") || name.startsWith("reactor-") -> "Reactor"
        else -> "Other"
    }
}
