package dev.velvet.minegrafana.paper.command

import dev.velvet.minegrafana.monitoring.application.service.MonitoringApplicationService
import dev.velvet.minegrafana.monitoring.application.service.ProfilerApplicationService
import dev.velvet.minegrafana.monitoring.domain.model.HealthGrade
import dev.velvet.minegrafana.monitoring.domain.model.ProfileEvent
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
    private val plugin: MineGrafanaPaper,
    private val monitoringService: MonitoringApplicationService,
    var profilerService: ProfilerApplicationService? = null
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendStatus(sender)
            return true
        }

        when (args[0].lowercase()) {
            "monitor", "health" -> handleMonitor(sender)
            "profile" -> handleProfile(sender, args.drop(1))
            "status" -> sendStatus(sender)
            "reload" -> handleReload(sender)
            else -> sender.sendMessage(Component.text("Unknown subcommand: ${args[0]}", NamedTextColor.RED))
        }
        return true
    }

    private fun handleMonitor(sender: CommandSender) {
        if (!sender.hasPermission("minegrafana.command.monitor")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }

        val health = monitoringService.getLatestHealth()
        if (health == null) {
            sender.sendMessage(Component.text("No metrics collected yet. Please wait...", NamedTextColor.YELLOW))
            return
        }

        val grade = health.grade()
        val gradeColor = when (grade) {
            HealthGrade.GOOD -> NamedTextColor.GREEN
            HealthGrade.WARNING -> NamedTextColor.YELLOW
            HealthGrade.CRITICAL -> NamedTextColor.RED
        }
        val tpsColor = when {
            health.tps.current >= 18.0 -> NamedTextColor.GREEN
            health.tps.current >= 15.0 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
        val memPercent = if (health.memory.maxMb > 0)
            (health.memory.usedMb.toDouble() / health.memory.maxMb * 100).toInt() else 0
        val avgPing = if (health.playerPings.isEmpty()) 0
            else health.playerPings.map { it.pingMs }.average().toInt()

        sender.sendMessage(Component.text("--- Server Health Report ---", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(Component.text("Grade: ", NamedTextColor.GRAY).append(Component.text(grade.name, gradeColor, TextDecoration.BOLD)))
        sender.sendMessage(Component.text("TPS: ", NamedTextColor.GRAY).append(Component.text("%.1f".format(health.tps.current), tpsColor))
            .append(Component.text(" (1m: %.1f | 5m: %.1f | 15m: %.1f)".format(health.tps.avg1m, health.tps.avg5m, health.tps.avg15m), NamedTextColor.DARK_GRAY)))
        sender.sendMessage(Component.text("MSPT: ", NamedTextColor.GRAY).append(Component.text("%.1fms avg / %.1fms p95".format(health.mspt.avg, health.mspt.percentile95),
            if (health.mspt.percentile95 > 50) NamedTextColor.RED else NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("CPU: ", NamedTextColor.GRAY).append(Component.text("%.1f%% process / %.1f%% system".format(health.cpu.process, health.cpu.system), NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Memory: ", NamedTextColor.GRAY).append(Component.text("${health.memory.usedMb}MB / ${health.memory.maxMb}MB ($memPercent%)",
            if (memPercent > 90) NamedTextColor.RED else NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Entities: ", NamedTextColor.GRAY).append(Component.text("${health.entities.sumOf { it.total }}", NamedTextColor.WHITE))
            .append(Component.text(" | Chunks: ", NamedTextColor.GRAY)).append(Component.text("${health.chunks.sumOf { it.loaded }}", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Players: ", NamedTextColor.GRAY).append(Component.text("${health.playerPings.size}", NamedTextColor.WHITE))
            .append(Component.text(" (avg ping: ${avgPing}ms)", NamedTextColor.DARK_GRAY)))
    }

    private fun handleProfile(sender: CommandSender, args: List<String>) {
        if (!sender.hasPermission("minegrafana.command.profile")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
            return
        }
        val profiler = profilerService
        if (profiler == null) {
            sender.sendMessage(Component.text("Profiler not initialized yet.", NamedTextColor.RED))
            return
        }

        val sub = args.firstOrNull()?.lowercase() ?: "start"

        when (sub) {
            "start" -> {
                if (profiler.isRunning()) {
                    sender.sendMessage(Component.text("Profiler is already running. Use /mg profile stop first.", NamedTextColor.RED))
                    return
                }
                val duration = args.getOrNull(1)?.toIntOrNull() ?: 30
                val event = when (args.getOrNull(2)?.lowercase()) {
                    "alloc" -> ProfileEvent.ALLOC
                    "wall" -> ProfileEvent.WALL
                    else -> ProfileEvent.CPU
                }
                try {
                    profiler.start(event, duration)
                    sender.sendMessage(Component.text("Profiler started! Engine: ${profiler.getEngineName()}, Event: ${event.name}, Duration: ${duration}s", NamedTextColor.GREEN))
                    sender.sendMessage(Component.text("Use /mg profile stop to stop early, or wait for auto-stop.", NamedTextColor.GRAY))
                } catch (e: Exception) {
                    sender.sendMessage(Component.text("Failed to start profiler: ${e.message}", NamedTextColor.RED))
                }
            }
            "stop" -> {
                if (!profiler.isRunning()) {
                    sender.sendMessage(Component.text("No profiler session is running.", NamedTextColor.RED))
                    return
                }
                try {
                    profiler.stop()
                    sender.sendMessage(Component.text("Profiler stopped! Flame graph generated.", NamedTextColor.GREEN))
                    val url = "http://localhost:${plugin.pluginConfig.web.port}/profiler"
                    sender.sendMessage(Component.text("View results: ", NamedTextColor.GRAY)
                        .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(url))))
                } catch (e: Exception) {
                    sender.sendMessage(Component.text("Failed to stop profiler: ${e.message}", NamedTextColor.RED))
                }
            }
            "view" -> {
                val url = "http://localhost:${plugin.pluginConfig.web.port}/profiler"
                sender.sendMessage(Component.text("Open profiler: ", NamedTextColor.GRAY)
                    .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))))
            }
            else -> sender.sendMessage(Component.text("Usage: /mg profile <start|stop|view> [duration] [cpu|alloc|wall]", NamedTextColor.RED))
        }
    }

    private fun sendStatus(sender: CommandSender) {
        val version = plugin.pluginMeta.version
        val springReady = plugin.isSpringReady()
        val port = plugin.pluginConfig.web.port

        sender.sendMessage(Component.text("--- mineGrafana v$version ---", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("Spring Boot: ", NamedTextColor.GRAY)
            .append(if (springReady) Component.text("READY", NamedTextColor.GREEN) else Component.text("STARTING", NamedTextColor.YELLOW)))
        sender.sendMessage(Component.text("Web Port: ", NamedTextColor.GRAY).append(Component.text("$port", NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Server ID: ", NamedTextColor.GRAY).append(Component.text(plugin.pluginConfig.serverId, NamedTextColor.WHITE)))
        sender.sendMessage(Component.text("Profiler: ", NamedTextColor.GRAY).append(Component.text(profilerService?.getEngineName() ?: "N/A", NamedTextColor.WHITE)))
        val url = "http://localhost:$port/"
        sender.sendMessage(Component.text("Dashboard: ", NamedTextColor.GRAY)
            .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(url))))
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
            return listOf("monitor", "profile", "status", "reload").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("profile", true)) {
            return listOf("start", "stop", "view").filter { it.startsWith(args[1].lowercase()) }
        }
        if (args.size == 4 && args[0].equals("profile", true) && args[1].equals("start", true)) {
            return listOf("cpu", "alloc", "wall").filter { it.startsWith(args[3].lowercase()) }
        }
        return emptyList()
    }
}
