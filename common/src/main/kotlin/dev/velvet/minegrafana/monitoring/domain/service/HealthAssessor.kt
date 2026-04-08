package dev.velvet.minegrafana.monitoring.domain.service

import dev.velvet.minegrafana.monitoring.domain.model.HealthGrade
import dev.velvet.minegrafana.monitoring.domain.model.ServerHealth

/**
 * Server health assessment domain service.
 * Pure domain logic — no framework dependency.
 */
class HealthAssessor {

    fun assess(health: ServerHealth): HealthAssessment {
        val grade = health.grade()
        val issues = mutableListOf<String>()

        if (health.tps.current < 15.0) issues.add("TPS critically low: ${health.tps.current}")
        else if (health.tps.current < 18.0) issues.add("TPS below normal: ${health.tps.current}")

        if (health.mspt.percentile95 > 50.0) issues.add("MSPT 95th percentile high: ${health.mspt.percentile95}ms")

        val memPercent = if (health.memory.maxMb > 0)
            (health.memory.usedMb.toDouble() / health.memory.maxMb * 100).toInt() else 0
        if (memPercent > 90) issues.add("Memory usage critical: $memPercent%")
        else if (memPercent > 80) issues.add("Memory usage high: $memPercent%")

        if (health.cpu.process > 90.0) issues.add("CPU usage critical: ${health.cpu.process}%")

        return HealthAssessment(grade, issues)
    }
}

data class HealthAssessment(
    val grade: HealthGrade,
    val issues: List<String>
)
