package dev.velvet.minegrafana.paper.spring

import dev.velvet.minegrafana.paper.MineGrafanaPaper
import dev.velvet.minegrafana.spring.AbstractSpringBridge

class PaperSpringBridge(private val plugin: MineGrafanaPaper) : AbstractSpringBridge() {
    override fun log(message: String) = plugin.logger.info(message)
    override fun logError(message: String, e: Exception?) {
        plugin.logger.severe(message)
        e?.printStackTrace()
    }
    override fun getDataFolderPath(): String = plugin.dataFolder.absolutePath
}
