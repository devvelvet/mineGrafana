package dev.velvet.minegrafana.paper.adapter

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight thread sampler -- Spark-style per-plugin CPU usage tracking.
 */
class ThreadProfiler {

    private val samplingThread = Thread({
        while (!Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(50) // Sample every 50ms (~20 times/sec)
                sample()
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) { }
        }
    }, "minegrafana-profiler").apply { isDaemon = true }

    private val pluginSamples = ConcurrentHashMap<String, AtomicLong>()
    private val classSamples = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()
    private val threadSamples = ConcurrentHashMap<String, AtomicLong>()

    // Total thread observation count (N threads per sample() call -> N increments)
    private val totalThreadObservations = AtomicLong(0)

    // ClassLoader -> plugin name mapping (most reliable for Bukkit plugins)
    private val classLoaderToPlugin = ConcurrentHashMap<ClassLoader, String>()
    // Package prefix -> plugin name fallback
    private val pluginPackages = ConcurrentHashMap<String, String>()

    fun init() {
        // First pass: register ClassLoaders + full package prefixes
        val allPrefixes = mutableMapOf<String, MutableSet<String>>() // prefix -> set of plugin names

        for (plugin in Bukkit.getPluginManager().plugins) {
            val name = plugin.name
            val mainClass = plugin.description.main
            val packagePrefix = mainClass.substringBeforeLast(".")

            classLoaderToPlugin[plugin.javaClass.classLoader] = name

            // Collect all candidate prefixes
            pluginPackages[packagePrefix] = name // Full prefix is always unique enough
            val parts = packagePrefix.split(".")
            for (i in 2..minOf(parts.size, 4)) {
                val shortPrefix = parts.take(i).joinToString(".")
                allPrefixes.getOrPut(shortPrefix) { mutableSetOf() }.add(name)
            }
        }

        // Second pass: only register short prefixes that are NOT shared between plugins
        for ((prefix, plugins) in allPrefixes) {
            if (plugins.size == 1) {
                pluginPackages[prefix] = plugins.first()
            }
            // Shared prefixes (e.g. "dev.velvet" used by both mineGrafana and HyperCosmetics) are skipped
        }

        // Start dedicated sampling thread (independent of tick events)
        samplingThread.start()
    }

    fun shutdown() {
        samplingThread.interrupt()
    }

    fun getRegisteredPlugins(): Map<String, String> = pluginPackages.toMap()

    fun sample() {
        val traces = Thread.getAllStackTraces()

        for ((thread, stackTrace) in traces) {
            if (stackTrace.isEmpty()) continue
            // Skip our own sampling thread + Spring/Reactor internal threads
            val tName = thread.name
            if (tName == "minegrafana-profiler" || tName.startsWith("reactor-") ||
                tName.startsWith("parallel-") || tName == "minegrafana-spring") continue

            totalThreadObservations.incrementAndGet()

            val threadName = categorizeThread(tName)
            threadSamples.getOrPut(threadName) { AtomicLong() }.incrementAndGet()

            // Identify plugin from stack trace (skip mineGrafana's own frames)
            val pluginName = identifyPluginByStackTrace(stackTrace)
            if (pluginName != null) {
                pluginSamples.getOrPut(pluginName) { AtomicLong() }.incrementAndGet()
                // Find the deepest plugin frame for hot class tracking
                for (frame in stackTrace) {
                    if (identifyPluginFromClassName(frame.className) == pluginName) {
                        val shortClass = frame.className.substringAfterLast(".")
                        classSamples.getOrPut(pluginName) { ConcurrentHashMap() }
                            .getOrPut(shortClass) { AtomicLong() }.incrementAndGet()
                        break
                    }
                }
            }
        }
    }

    /** Per-plugin CPU usage percentage (0.0 ~ 100.0) */
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

    fun getThreadSamples(): Map<String, Long> = threadSamples.mapValues { it.value.get() }

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

    /** Identify plugin by thread's context ClassLoader */
    private fun identifyPluginByClassLoader(thread: Thread): String? {
        val cl = thread.contextClassLoader ?: return null
        return classLoaderToPlugin[cl]
    }

    /** Identify plugin by scanning stack trace frames (skip self to avoid false attribution) */
    private fun identifyPluginByStackTrace(stackTrace: Array<StackTraceElement>): String? {
        for (frame in stackTrace) {
            val plugin = identifyPluginFromClassName(frame.className)
            // Skip mineGrafana's own frames — our profiler code is always at the top of the Server thread stack
            if (plugin != null && plugin != "mineGrafana") return plugin
        }
        return null
    }

    private fun identifyPluginFromClassName(className: String): String? {
        // Skip JDK / Minecraft / Paper internals
        if (className.startsWith("java.") || className.startsWith("jdk.") ||
            className.startsWith("sun.") || className.startsWith("net.minecraft.") ||
            className.startsWith("org.bukkit.") || className.startsWith("io.papermc.") ||
            className.startsWith("io.netty.") || className.startsWith("com.mojang.") ||
            className.startsWith("org.springframework.") || className.startsWith("reactor.")) {
            return null
        }
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
