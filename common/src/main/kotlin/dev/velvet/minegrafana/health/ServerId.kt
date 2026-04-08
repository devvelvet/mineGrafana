package dev.velvet.minegrafana.health

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
