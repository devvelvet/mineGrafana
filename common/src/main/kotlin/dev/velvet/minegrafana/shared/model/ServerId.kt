package dev.velvet.minegrafana.shared.model

/**
 * Unique server identifier. Wraps the server-id value from config.yml.
 */
@JvmInline
value class ServerId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServerId must not be blank" }
    }

    override fun toString(): String = value
}
