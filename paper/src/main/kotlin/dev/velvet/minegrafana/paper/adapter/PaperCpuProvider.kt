package dev.velvet.minegrafana.paper.adapter

import dev.velvet.minegrafana.monitoring.domain.model.CpuSnapshot
import java.lang.management.ManagementFactory

object PaperCpuProvider {

    fun collect(): CpuSnapshot {
        val osBean = ManagementFactory.getOperatingSystemMXBean()

        var processCpu = -1.0
        var systemCpu = -1.0

        // Retrieve detailed CPU info from com.sun.management.OperatingSystemMXBean
        if (osBean is com.sun.management.OperatingSystemMXBean) {
            processCpu = osBean.processCpuLoad * 100.0
            systemCpu = osBean.cpuLoad * 100.0
        }

        return CpuSnapshot(
            process = Math.round(processCpu * 100.0) / 100.0,
            system = Math.round(systemCpu * 100.0) / 100.0
        )
    }
}
