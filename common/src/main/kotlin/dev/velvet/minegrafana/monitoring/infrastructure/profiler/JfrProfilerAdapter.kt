package dev.velvet.minegrafana.monitoring.infrastructure.profiler

import dev.velvet.minegrafana.monitoring.domain.model.FlameGraph
import dev.velvet.minegrafana.monitoring.domain.model.ProfileEvent
import dev.velvet.minegrafana.monitoring.domain.service.ProfilerEngine
import jdk.jfr.Configuration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JDK Flight Recorder based profiler.
 * Used in environments where async-profiler is not supported, such as Windows/macOS.
 */
class JfrProfilerAdapter(private val dataFolder: Path) : ProfilerEngine {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private var recording: Recording? = null
    private var stopFuture: ScheduledFuture<*>? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "minegrafana-profiler").apply { isDaemon = true }
    }

    override fun isAvailable(): Boolean = true // JFR is always available on JDK 11+

    override fun start(event: ProfileEvent, duration: Duration) {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException("Profiler is already running")
        }

        try {
            val jfrConfig = Configuration.getConfiguration("profile")
            recording = Recording(jfrConfig).apply {
                name = "minegrafana-${event.name.lowercase()}"
                start()
            }
            logger.info("JFR profiler started. Event: ${event.name}, Duration: ${duration.seconds}s")

            // Auto-stop after duration
            stopFuture = scheduler.schedule({ stopInternal() }, duration.seconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
    }

    override fun stop(): FlameGraph {
        return stopInternal()
    }

    override fun isRunning(): Boolean = running.get()

    private fun stopInternal(): FlameGraph {
        if (!running.compareAndSet(true, false)) {
            throw IllegalStateException("Profiler is not running")
        }

        try {
            stopFuture?.cancel(false)

            val jfrFile = dataFolder.resolve("profiler-${System.currentTimeMillis()}.jfr")
            Files.createDirectories(dataFolder)

            recording?.apply {
                stop()
                dump(jfrFile)
                close()
            }
            recording = null

            // Parse JFR and generate flame graph HTML
            val html = generateFlameGraphFromJfr(jfrFile)

            // Cleanup JFR file
            Files.deleteIfExists(jfrFile)

            return FlameGraph(html, Instant.now())
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
    }

    private fun generateFlameGraphFromJfr(jfrFile: Path): String {
        val stackCounts = ConcurrentHashMap<String, Long>()

        try {
            RecordingFile(jfrFile).use { rf ->
                while (rf.hasMoreEvents()) {
                    val event = rf.readEvent()
                    if (event.eventType.name == "jdk.ExecutionSample" ||
                        event.eventType.name == "jdk.ObjectAllocationInNewTLAB") {
                        val stackTrace = event.stackTrace ?: continue
                        val frames = stackTrace.frames.reversed().joinToString(";") { frame ->
                            val method = frame.method
                            "${method.type.name}.${method.name}"
                        }
                        if (frames.isNotBlank()) {
                            stackCounts.merge(frames, 1L) { a, b -> a + b }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse JFR file: ${e.message}")
        }

        return buildFlameGraphHtml(stackCounts)
    }

    private fun buildFlameGraphHtml(stacks: Map<String, Long>): String {
        // Generate a simple interactive flame graph using d3-flame-graph
        val collapsed = stacks.entries.sortedByDescending { it.value }
            .joinToString("\n") { "${it.key} ${it.value}" }

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<title>mineGrafana Flame Graph</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/d3-flame-graph@4/dist/d3-flamegraph.css"/>
<style>body{margin:0;font-family:sans-serif;background:#1a1a2e;color:#eee}
#header{padding:12px 20px;background:#16213e;border-bottom:1px solid #333}
h1{margin:0;font-size:18px}
.info{color:#888;font-size:13px;margin-top:4px}
#chart{padding:8px}</style>
</head>
<body>
<div id="header">
<h1>mineGrafana Flame Graph</h1>
<div class="info">Samples: ${stacks.values.sum()} | Unique stacks: ${stacks.size}</div>
</div>
<div id="chart"></div>
<script src="https://cdn.jsdelivr.net/npm/d3@7"></script>
<script src="https://cdn.jsdelivr.net/npm/d3-flame-graph@4/dist/d3-flamegraph.min.js"></script>
<script>
const data = parseCollapsed(`$collapsed`);
const chart = flamegraph().width(document.body.clientWidth).cellHeight(18)
  .transitionDuration(300).tooltip(true).color(function(d){
    const name=d.data.name||'';
    if(name.startsWith('net.minecraft'))return'#e85d04';
    if(name.startsWith('org.bukkit')||name.startsWith('io.papermc'))return'#f77f00';
    if(name.startsWith('java.')||name.startsWith('jdk.'))return'#4895ef';
    return'#fcbf49';
  });
d3.select("#chart").datum(data).call(chart);

function parseCollapsed(text){
  const root={name:"root",value:0,children:[]};
  text.trim().split("\\n").forEach(line=>{
    const sp=line.lastIndexOf(" ");
    if(sp<0)return;
    const stack=line.substring(0,sp).split(";");
    const count=parseInt(line.substring(sp+1));
    let node=root;
    root.value+=count;
    stack.forEach(frame=>{
      let child=node.children.find(c=>c.name===frame);
      if(!child){child={name:frame,value:0,children:[]};node.children.push(child);}
      child.value+=count;
      node=child;
    });
  });
  return root;
}
</script>
</body>
</html>
""".trimIndent()
    }
}
