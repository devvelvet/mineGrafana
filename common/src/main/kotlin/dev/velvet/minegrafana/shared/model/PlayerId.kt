package dev.velvet.minegrafana.shared.model

import java.util.UUID

/**
 * Unique player identifier. Wraps a Minecraft UUID.
 */
@JvmInline
value class PlayerId(val uuid: UUID) {
    override fun toString(): String = uuid.toString()

    companion object {
        fun fromString(value: String): PlayerId = PlayerId(UUID.fromString(value))
    }
}
