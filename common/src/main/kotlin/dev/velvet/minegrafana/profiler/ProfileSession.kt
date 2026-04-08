package dev.velvet.minegrafana.profiler

import dev.velvet.minegrafana.health.ServerId
import java.time.Duration
import java.time.Instant

class ProfileSession private constructor(
    val serverId: ServerId,
    val event: ProfileEvent,
    val startedAt: Instant,
    private var _stoppedAt: Instant? = null,
    private var _result: FlameGraph? = null,
    private var _status: ProfileStatus = ProfileStatus.RUNNING
) {
    val stoppedAt: Instant? get() = _stoppedAt
    val result: FlameGraph? get() = _result
    val status: ProfileStatus get() = _status
    val duration: Duration get() = Duration.between(startedAt, _stoppedAt ?: Instant.now())

    fun stop(flameGraph: FlameGraph) {
        require(_status == ProfileStatus.RUNNING) { "Profile session is not running" }
        _stoppedAt = Instant.now()
        _result = flameGraph
        _status = ProfileStatus.COMPLETED
    }

    fun fail(reason: String) {
        _stoppedAt = Instant.now()
        _status = ProfileStatus.FAILED
    }

    companion object {
        fun start(serverId: ServerId, event: ProfileEvent): ProfileSession =
            ProfileSession(serverId = serverId, event = event, startedAt = Instant.now())
    }
}

enum class ProfileEvent { CPU, ALLOC, WALL }

enum class ProfileStatus { RUNNING, COMPLETED, FAILED }

data class FlameGraph(val html: String, val generatedAt: Instant = Instant.now())
