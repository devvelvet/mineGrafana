package dev.velvet.minegrafana.monitoring.application.service

import dev.velvet.minegrafana.monitoring.domain.model.ProfileEvent
import dev.velvet.minegrafana.monitoring.domain.model.ProfileSession
import dev.velvet.minegrafana.monitoring.domain.model.ProfileStatus
import dev.velvet.minegrafana.monitoring.domain.service.ProfilerEngine
import dev.velvet.minegrafana.shared.model.ServerId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

@Service
class ProfilerApplicationService(
    private val engine: ProfilerEngine,
    @Value("\${minegrafana.server-id:default}") private val serverIdStr: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val currentSession = AtomicReference<ProfileSession?>()

    fun start(event: ProfileEvent = ProfileEvent.CPU, durationSeconds: Int = 30): ProfileSession {
        if (engine.isRunning()) throw IllegalStateException("Profiler is already running")
        val session = ProfileSession.start(ServerId(serverIdStr), event)
        currentSession.set(session)
        engine.start(event, Duration.ofSeconds(durationSeconds.toLong()))
        return session
    }

    fun stop(): ProfileSession {
        val session = currentSession.get() ?: throw IllegalStateException("No active profiler session")
        if (session.status != ProfileStatus.RUNNING) throw IllegalStateException("Session is not running")
        try {
            val flameGraph = engine.stop()
            session.stop(flameGraph)
        } catch (e: Exception) {
            session.fail(e.message ?: "Unknown error")
            logger.error("Profiler stop failed", e)
        }
        return session
    }

    fun getSession(): ProfileSession? = currentSession.get()
    fun isRunning(): Boolean = engine.isRunning()
    fun getEngineName(): String = engine.javaClass.simpleName
}
