package dev.velvet.minegrafana.shared.event

import java.time.Instant
import java.util.UUID

/**
 * Base class for all domain events.
 * Does not extend Spring ApplicationEvent directly (Domain layer is framework-independent).
 * The Application layer converts and publishes these as Spring Events.
 */
abstract class DomainEvent(
    val eventId: UUID = UUID.randomUUID(),
    val occurredAt: Instant = Instant.now()
)
