package dev.velvet.minegrafana.paper

import dev.velvet.minegrafana.config.ConfigLoader
import dev.velvet.minegrafana.config.MessagesConfig
import dev.velvet.minegrafana.config.PluginConfig
import dev.velvet.minegrafana.grafana.GrafanaProvisioner
import dev.velvet.minegrafana.metrics.MinecraftMeterBinder
import dev.velvet.minegrafana.metrics.WorldSnapshot
import org.bukkit.Bukkit
import org.bukkit.block.Hopper
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.management.ManagementFactory
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

    // Cached on main thread, read by Prometheus scrape thread
    @Volatile private var cachedWorldStats: List<WorldSnapshot> = emptyList()
    @Volatile private var cachedEntityTypes: Map<Pair<String, String>, Int> = emptyMap()
    @Volatile private var cachedPlayerPings: Map<String, Int> = emptyMap()

    override fun onEnable() {
        if (!loadConfiguration()) return
        if (!startSpringBoot()) return
        registerListeners()
        scheduleServiceWiring()
        logger.info("mineGrafana v${pluginMeta.version} enabled!")
    }

    override fun onDisable() {
        threadProfiler.shutdown()
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
        server.pluginManager.registerEvents(
            TickEventListener(msptProvider, tickDistribution), this
        )
    }

    private fun scheduleServiceWiring() {
        server.scheduler.runTaskLaterAsynchronously(this, Runnable {
            if (!springBridge!!.awaitReady(30)) {
                logger.severe("Spring Boot failed to start within 30 seconds!")
                return@Runnable
            }
            logger.info("Spring Boot is ready. Initializing...")

            // Init thread profiler (all plugins loaded by now)
            threadProfiler.init()

            // Get MeterBinder and wire providers
            val meterBinder = springBridge!!.getBean(MinecraftMeterBinder::class.java)
            if (meterBinder != null) {
                // Providers read from cached data only (thread-safe, no Bukkit API calls)
                meterBinder.setProviders(
                    pluginCpu = { threadProfiler.getPluginCpuPercent() },
                    threadSamples = { threadProfiler.getThreadSamples() },
                    hotClasses = { threadProfiler.getAllHotClasses() },
                    tickDist = { longArrayOf(tickDistribution.under5ms.get(), tickDistribution.under10ms.get(), tickDistribution.under25ms.get(), tickDistribution.under50ms.get(), tickDistribution.over50ms.get()) },
                    worldStats = { cachedWorldStats },
                    entityTypes = { cachedEntityTypes },
                    playerPings = { cachedPlayerPings }
                )

                // Periodic cache update on MAIN thread (Bukkit API requires it)
                val intervalTicks = pluginConfig.features.monitoring.collectionIntervalSeconds * 20L
                server.scheduler.runTaskTimer(this@MineGrafanaPaper, Runnable {
                    cachedWorldStats = collectWorldStats()
                    cachedEntityTypes = collectEntityTypes()
                    cachedPlayerPings = Bukkit.getOnlinePlayers().associate { it.name to it.ping }
                    updateCache(meterBinder)
                    meterBinder.registerDynamicGauges()
                }, 40L, intervalTicks)

                logger.info("Monitoring initialized. Interval: ${pluginConfig.features.monitoring.collectionIntervalSeconds}s")
            }

            // Grafana
            if (pluginConfig.grafana.autoProvision) {
                springBridge!!.getBean(GrafanaProvisioner::class.java)?.provision(
                    pluginConfig.grafana, "http://localhost:${pluginConfig.web.port}/metrics", "paper"
                )
            }

            // Commands
            server.scheduler.runTask(this@MineGrafanaPaper, Runnable {
                val handler = MineGrafanaCommand(this@MineGrafanaPaper)
                val cmd = object : org.bukkit.command.Command("mg", "mineGrafana", "/mg <subcommand>", listOf("minegrafana")) {
                    override fun execute(sender: org.bukkit.command.CommandSender, label: String, args: Array<out String>) = handler.onCommand(sender, this, label, args)
                    override fun tabComplete(sender: org.bukkit.command.CommandSender, alias: String, args: Array<out String>) = handler.onTabComplete(sender, this, alias, args).toMutableList()
                }
                server.commandMap.register("minegrafana", cmd)
                logger.info("Command /mg registered.")
            })
        }, 20L)
    }

    // ─── Direct Bukkit API metric collection (no intermediate abstraction) ───

    private fun updateCache(m: MinecraftMeterBinder) {
        val tps = Bukkit.getTPS()
        m.tpsCurrent = tps[0].coerceIn(0.0, 20.0)
        m.tps1m = tps[0].coerceIn(0.0, 20.0)
        m.tps5m = tps[1].coerceIn(0.0, 20.0)
        m.tps15m = tps[2].coerceIn(0.0, 20.0)

        val mspt = msptProvider.collect()
        m.msptAvg = mspt.avg; m.msptMin = mspt.min; m.msptMax = mspt.max; m.msptP95 = mspt.p95

        val os = ManagementFactory.getOperatingSystemMXBean()
        if (os is com.sun.management.OperatingSystemMXBean) {
            m.cpuProcess = Math.round(os.processCpuLoad * 10000.0) / 100.0
            m.cpuSystem = Math.round(os.cpuLoad * 10000.0) / 100.0
        }

        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        m.memUsedMb = heap.used / (1024 * 1024)
        m.memMaxMb = heap.max / (1024 * 1024)
        m.memFreePercent = if (m.memMaxMb > 0) Math.round((m.memMaxMb - m.memUsedMb).toDouble() / m.memMaxMb * 10000.0) / 100.0 else 100.0

        m.playersOnline = Bukkit.getOnlinePlayers().size
        m.pingAvg = Bukkit.getOnlinePlayers().let { if (it.isEmpty()) 0.0 else it.map { p -> p.ping }.average() }

        var entities = 0; var chunks = 0; var tiles = 0; var hoppersCount = 0; var redstone = 0
        for (world in Bukkit.getWorlds()) {
            entities += world.entityCount
            val loaded = world.loadedChunks
            chunks += loaded.size
            for (chunk in loaded) {
                for (te in chunk.tileEntities) {
                    tiles++
                    when {
                        te is Hopper -> hoppersCount++
                        te.type == Material.COMPARATOR || te.type == Material.REPEATER -> redstone++
                    }
                }
            }
        }
        m.entitiesTotal = entities; m.chunksTotal = chunks; m.tileEntitiesTotal = tiles
        m.redstoneActive = redstone; m.hoppers = hoppersCount

        try {
            val dir = Bukkit.getWorldContainer()
            m.diskUsedMb = (dir.totalSpace - dir.usableSpace) / (1024 * 1024)
            m.diskFreeMb = dir.usableSpace / (1024 * 1024)
            m.diskTotalMb = dir.totalSpace / (1024 * 1024)
            m.worldSizeMb = Bukkit.getWorlds().sumOf { folderSize(it.worldFolder) } / (1024 * 1024)
        } catch (_: Exception) {}
    }

    private fun collectWorldStats(): List<WorldSnapshot> = Bukkit.getWorlds().map { w ->
        WorldSnapshot(w.name, w.entityCount, w.loadedChunks.size, w.loadedChunks.sumOf { it.tileEntities.size })
    }

    private fun collectEntityTypes(): Map<Pair<String, String>, Int> {
        val result = mutableMapOf<Pair<String, String>, Int>()
        for (w in Bukkit.getWorlds()) for (e in w.entities) {
            val key = w.name to e.type.name
            result[key] = (result[key] ?: 0) + 1
        }
        return result
    }

    private fun folderSize(dir: File): Long {
        var size = 0L; val files = dir.listFiles() ?: return 0
        for (f in files) size += if (f.isDirectory) folderSize(f) else f.length()
        return size
    }
}
