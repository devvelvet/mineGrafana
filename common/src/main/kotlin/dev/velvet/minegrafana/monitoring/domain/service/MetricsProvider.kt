package dev.velvet.minegrafana.monitoring.domain.service

import dev.velvet.minegrafana.monitoring.domain.model.*

interface MetricsProvider {
    fun collectTps(): TpsSnapshot
    fun collectMspt(): MsptSnapshot
    fun collectCpu(): CpuSnapshot
    fun collectMemory(): MemorySnapshot
    fun collectGc(): GcSnapshot
    fun collectEntities(): List<WorldEntityCount>
    fun collectChunks(): List<WorldChunkCount>
    fun collectPlayerPings(): List<PlayerPing>
    fun collectWorldStats(): List<WorldStats> = emptyList()
    fun collectRedstone(): RedstoneStats = RedstoneStats()
    fun collectNetwork(): NetworkStats = NetworkStats()
    fun collectDisk(): DiskStats = DiskStats()
}
