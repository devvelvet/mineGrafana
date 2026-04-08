package dev.velvet.minegrafana.velocity

import dev.velvet.minegrafana.spring.AbstractSpringBridge
import dev.velvet.minegrafana.velocity.MineGrafanaVelocity

class VelocitySpringBridge(private val plugin: MineGrafanaVelocity) : AbstractSpringBridge() {
    override fun log(message: String) = plugin.logger.info(message)
    override fun logError(message: String, e: Exception?) {
        plugin.logger.error(message, e)
    }
    override fun getDataFolderPath(): String = plugin.dataDirectory.toAbsolutePath().toString()
}
