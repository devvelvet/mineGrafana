package dev.velvet.minegrafana.monitoring.infrastructure.profiler

import dev.velvet.minegrafana.monitoring.domain.model.FlameGraph
import dev.velvet.minegrafana.monitoring.domain.model.ProfileEvent
import dev.velvet.minegrafana.monitoring.domain.service.ProfilerEngine
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Profiler based on the async-profiler native library (Linux only).
 * Extracts the native library from the JAR and loads it.
 * If async-profiler is not available, isAvailable() returns false.
 */
class AsyncProfilerAdapter(private val dataFolder: Path) : ProfilerEngine {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private var profilerInstance: Any? = null // AsyncProfiler instance via reflection
    private var nativeLoaded = false
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "minegrafana-async-profiler").apply { isDaemon = true }
    }
    private var stopFuture: ScheduledFuture<*>? = null
    private var currentEvent: ProfileEvent = ProfileEvent.CPU

    init {
        tryLoadNative()
    }

    override fun isAvailable(): Boolean = nativeLoaded

    override fun start(event: ProfileEvent, duration: Duration) {
        if (!nativeLoaded) throw IllegalStateException("async-profiler native library not loaded")
        if (!running.compareAndSet(false, true)) throw IllegalStateException("Profiler already running")

        try {
            currentEvent = event
            val eventStr = when (event) {
                ProfileEvent.CPU -> "cpu"
                ProfileEvent.ALLOC -> "alloc"
                ProfileEvent.WALL -> "wall"
            }

            executeProfilerCommand("start,event=$eventStr")
            logger.info("async-profiler started. Event: $eventStr, Duration: ${duration.seconds}s")

            stopFuture = scheduler.schedule({ stopInternal() }, duration.seconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
    }

    override fun stop(): FlameGraph = stopInternal()

    override fun isRunning(): Boolean = running.get()

    private fun stopInternal(): FlameGraph {
        if (!running.compareAndSet(true, false)) {
            throw IllegalStateException("Profiler is not running")
        }

        try {
            stopFuture?.cancel(false)

            val htmlFile = dataFolder.resolve("flamegraph-${System.currentTimeMillis()}.html")
            Files.createDirectories(dataFolder)

            executeProfilerCommand("stop,file=${htmlFile.toAbsolutePath()},flamegraph")

            val html = if (Files.exists(htmlFile)) {
                Files.readString(htmlFile).also { Files.deleteIfExists(htmlFile) }
            } else {
                "<html><body><h1>Flame graph generation failed</h1></body></html>"
            }

            return FlameGraph(html, Instant.now())
        } catch (e: Exception) {
            running.set(false) // Reset on failure so it can be restarted
            throw e
        }
    }

    private fun tryLoadNative() {
        try {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()

            if (!os.contains("linux")) {
                logger.info("async-profiler is Linux-only. Current OS: $os")
                return
            }

            val libName = when {
                arch.contains("amd64") || arch.contains("x86_64") -> "libasyncProfiler-linux-x64.so"
                arch.contains("aarch64") -> "libasyncProfiler-linux-aarch64.so"
                else -> {
                    logger.info("Unsupported architecture for async-profiler: $arch")
                    return
                }
            }

            // Try to extract from classpath
            val resourcePath = "/natives/$libName"
            val inputStream: InputStream? = javaClass.getResourceAsStream(resourcePath)

            if (inputStream == null) {
                // Try system-installed async-profiler
                tryLoadSystemProfiler()
                return
            }

            val extractedLib = dataFolder.resolve(libName)
            Files.createDirectories(dataFolder)
            inputStream.use { Files.copy(it, extractedLib) }

            System.load(extractedLib.toAbsolutePath().toString())

            // Load via reflection
            val clazz = Class.forName("one.profiler.AsyncProfiler")
            profilerInstance = clazz.getMethod("getInstance").invoke(null)
            nativeLoaded = true
            logger.info("async-profiler native library loaded successfully")
        } catch (e: Exception) {
            logger.info("async-profiler not available: ${e.message}. JFR fallback will be used.")
            nativeLoaded = false
        }
    }

    private fun tryLoadSystemProfiler() {
        try {
            val clazz = Class.forName("one.profiler.AsyncProfiler")
            profilerInstance = clazz.getMethod("getInstance").invoke(null)
            nativeLoaded = true
            logger.info("Using system-installed async-profiler")
        } catch (_: Exception) {
            nativeLoaded = false
        }
    }

    private fun executeProfilerCommand(command: String): String {
        val instance = profilerInstance ?: throw IllegalStateException("async-profiler not loaded")
        val method = instance.javaClass.getMethod("execute", String::class.java)
        return method.invoke(instance, command) as String
    }
}
