package dev.velvet.minegrafana.paper

import dev.velvet.minegrafana.monitoring.application.service.MonitoringApplicationService
import dev.velvet.minegrafana.monitoring.application.service.ProfilerApplicationService
import dev.velvet.minegrafana.monitoring.infrastructure.adapter.MinecraftMeterBinder
import dev.velvet.minegrafana.monitoring.infrastructure.grafana.GrafanaProvisioner
import dev.velvet.minegrafana.paper.adapter.PaperMetricsProvider
import dev.velvet.minegrafana.paper.adapter.PaperMsptProvider
import dev.velvet.minegrafana.paper.adapter.ThreadProfiler
import dev.velvet.minegrafana.paper.adapter.TickDistribution
import dev.velvet.minegrafana.paper.command.MineGrafanaCommand
import dev.velvet.minegrafana.paper.listener.TickEventListener
import dev.velvet.minegrafana.paper.spring.PaperSpringBridge
import dev.velvet.minegrafana.paper.task.MetricsCollectionTask
import dev.velvet.minegrafana.shared.config.ConfigLoader
import dev.velvet.minegrafana.shared.config.MessagesConfig
import dev.velvet.minegrafana.shared.config.PluginConfig
import dev.velvet.minegrafana.shared.model.ServerId
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class MineGrafanaPaper : JavaPlugin() {

    lateinit var pluginConfig: PluginConfig
        private set
    lateinit var messagesConfig: MessagesConfig
        private set

    private var springBridge: PaperSpringBridge? = null
    private val msptProvider = PaperMsptProvider()
    private val threadProfiler = ThreadProfiler()
    private val tickDistribution = TickDistribution()

    override fun onEnable() {
        if (!loadConfiguration()) return
        if (!startSpringBoot()) return
        registerListeners()
        scheduleServiceWiring()
        logger.info("mineGrafana v${pluginMeta.version} enabled!")
    }

    override fun onDisable() {
        server.scheduler.cancelTasks(this)
        springBridge?.stop()
        logger.info("mineGrafana disabled.")
    }

    fun reloadPluginConfig() {
        pluginConfig = ConfigLoader.loadConfig(dataFolder.toPath())
        messagesConfig = ConfigLoader.loadMessages(dataFolder.toPath())
        logger.info("Configuration reloaded.")
    }

    fun isSpringReady(): Boolean = springBridge?.isReady() == true

    // ─── Private setup methods ───

    private fun loadConfiguration(): Boolean {
        return try {
            pluginConfig = ConfigLoader.loadConfig(dataFolder.toPath())
            messagesConfig = ConfigLoader.loadMessages(dataFolder.toPath())
            logger.info("Configuration loaded.")
            true
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to load configuration", e)
            server.pluginManager.disablePlugin(this)
            false
        }
    }

    private fun startSpringBoot(): Boolean {
        return try {
            springBridge = PaperSpringBridge(this)
            springBridge!!.start(pluginConfig)
            logger.info("Spring Boot starting on port ${pluginConfig.web.port}...")
            true
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to start Spring Boot", e)
            server.pluginManager.disablePlugin(this)
            false
        }
    }

    private fun registerListeners() {
        threadProfiler.init()
        server.pluginManager.registerEvents(
            TickEventListener(msptProvider, threadProfiler, tickDistribution), this
        )
    }

    private fun scheduleServiceWiring() {
        server.scheduler.runTaskLaterAsynchronously(this, Runnable {
            if (!springBridge!!.awaitReady(30)) {
                logger.severe("Spring Boot failed to start within 30 seconds!")
                return@Runnable
            }
            logger.info("Spring Boot is ready. Initializing services...")

            wireMonitoring()
            wireGrafana()

            // Commands must be registered on the main thread
            server.scheduler.runTask(this@MineGrafanaPaper, Runnable {
                wireProfilerAndCommands()
            })
        }, 20L)
    }

    private fun wireMonitoring() {
        val monitoringService = springBridge!!.getBean(MonitoringApplicationService::class.java) ?: return
        monitoringService.metricsProvider = PaperMetricsProvider(msptProvider)

        val meterBinder = springBridge!!.getBean(MinecraftMeterBinder::class.java)
        meterBinder?.pluginCpuProvider = { threadProfiler.getPluginCpuPercent() }
        meterBinder?.threadSamplesProvider = { threadProfiler.getThreadSamples() }
        meterBinder?.hotClassesProvider = { threadProfiler.getAllHotClasses() }
        meterBinder?.tickDistProvider = {
            longArrayOf(
                tickDistribution.under5ms.get(), tickDistribution.under10ms.get(),
                tickDistribution.under25ms.get(), tickDistribution.under50ms.get(),
                tickDistribution.over50ms.get()
            )
        }

        val intervalTicks = pluginConfig.features.monitoring.collectionIntervalSeconds * 20L
        MetricsCollectionTask(monitoringService, ServerId(pluginConfig.serverId), meterBinder)
            .runTaskTimer(this, 40L, intervalTicks)

        logger.info("Monitoring initialized. Interval: ${pluginConfig.features.monitoring.collectionIntervalSeconds}s")
    }

    private fun wireGrafana() {
        if (!pluginConfig.grafana.autoProvision) return
        val provisioner = springBridge!!.getBean(GrafanaProvisioner::class.java) ?: return
        provisioner.provision(pluginConfig.grafana, "http://localhost:${pluginConfig.web.port}/metrics", "paper")
    }

    private fun wireProfilerAndCommands() {
        val monitoringService = springBridge!!.getBean(MonitoringApplicationService::class.java)
        val profilerService = springBridge!!.getBean(ProfilerApplicationService::class.java)

        if (profilerService != null) {
            logger.info("Profiler initialized. Engine: ${profilerService.getEngineName()}")
        }

        if (monitoringService != null) {
            val handler = MineGrafanaCommand(this, monitoringService, profilerService)

            // Paper plugins don't support getCommand() — register via Bukkit CommandMap
            val mgCommand = object : org.bukkit.command.Command(
                "mg", "mineGrafana main command", "/mg <subcommand>", listOf("minegrafana")
            ) {
                override fun execute(sender: org.bukkit.command.CommandSender, label: String, args: Array<out String>): Boolean {
                    return handler.onCommand(sender, this, label, args)
                }
                override fun tabComplete(sender: org.bukkit.command.CommandSender, alias: String, args: Array<out String>): MutableList<String> {
                    return handler.onTabComplete(sender, this, alias, args).toMutableList()
                }
            }
            server.commandMap.register("minegrafana", mgCommand)
            logger.info("Command /mg registered.")
        }
    }
}
