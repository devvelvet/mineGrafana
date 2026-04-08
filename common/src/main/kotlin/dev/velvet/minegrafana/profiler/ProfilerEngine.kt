package dev.velvet.minegrafana.profiler

import dev.velvet.minegrafana.profiler.FlameGraph
import dev.velvet.minegrafana.profiler.ProfileEvent
import java.time.Duration

/**
 * Profiler engine interface.
 * Implemented in the infrastructure layer via AsyncProfilerAdapter / JfrProfilerAdapter.
 */
interface ProfilerEngine {
    fun isAvailable(): Boolean
    fun start(event: ProfileEvent, duration: Duration)
    fun stop(): FlameGraph
    fun isRunning(): Boolean
}
