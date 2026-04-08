package dev.velvet.minegrafana.paper.command

import dev.velvet.minegrafana.paper.MineGrafanaPaper
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class MineGrafanaCommand(
    private val plugin: MineGrafanaPaper
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendStatus(sender)
            return true
        }

        when (args[0].lowercase()) {
            "status" -> sendStatus(sender)
            "reload" -> handleReload(sender)
            else -> sender.sendMessage(Component.text("Usage: /mg <status|reload>", NamedTextColor.RED))
        }
        return true
    }

    private fun sendStatus(sender: CommandSender) {
        val version = plugin.pluginMeta.version
        val springReady = plugin.isSpringReady()
        val port = plugin.pluginConfig.web.port

        sender.sendMessage(Component.text("--- mineGrafana v$version ---", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(Component.text("Spring Boot: ", NamedTextColor.GRAY)
            .append(if (springReady) Component.text("READY", NamedTextColor.GREEN) else Component.text("STARTING", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("Metrics: ", NamedTextColor.GRAY)
            .append(Component.text("http://localhost:$port/metrics", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl("http://localhost:$port/metrics"))))
        sender.sendMessage(Component.text("Server ID: ", NamedTextColor.GRAY)
            .append(Component.text(plugin.pluginConfig.serverId, NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Interval: ", NamedTextColor.GRAY)
            .append(Component.text("${plugin.pluginConfig.features.monitoring.collectionIntervalSeconds}s", NamedTextColor.WHITE)))
        val grafanaUrl = plugin.pluginConfig.grafana.url
        sender.sendMessage(Component.text("Grafana: ", NamedTextColor.GRAY)
            .append(Component.text(grafanaUrl, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(grafanaUrl))))
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("minegrafana.command.reload")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        plugin.reloadPluginConfig()
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("status", "reload").filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}
