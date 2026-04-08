package dev.velvet.minegrafana.monitoring.domain.event

import dev.velvet.minegrafana.monitoring.domain.model.ServerHealth
import dev.velvet.minegrafana.shared.event.DomainEvent

/**
 * Published when server health metrics collection is complete.
 * Subscribed by Alert BC for threshold evaluation.
 */
class HealthCollectedEvent(val serverHealth: ServerHealth) : DomainEvent()
