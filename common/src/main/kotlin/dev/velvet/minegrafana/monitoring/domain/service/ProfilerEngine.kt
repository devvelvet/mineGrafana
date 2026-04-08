package dev.velvet.minegrafana.monitoring.domain.service

import dev.velvet.minegrafana.monitoring.domain.model.FlameGraph
import dev.velvet.minegrafana.monitoring.domain.model.ProfileEvent
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
