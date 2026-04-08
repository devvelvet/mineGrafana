package dev.velvet.minegrafana.spring

import dev.velvet.minegrafana.shared.event.DomainEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Publisher that bridges Domain Events to Spring ApplicationEvents.
 * Application layer services publish events through this interface.
 */
fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}

@Configuration
class EventPublisherConfig {

    @Bean
    fun domainEventPublisher(applicationEventPublisher: ApplicationEventPublisher): DomainEventPublisher {
        return DomainEventPublisher { event ->
            applicationEventPublisher.publishEvent(event)
        }
    }
}
