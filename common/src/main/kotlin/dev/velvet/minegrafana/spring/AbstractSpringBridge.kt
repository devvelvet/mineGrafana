package dev.velvet.minegrafana.spring

import dev.velvet.minegrafana.shared.config.PluginConfig
import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.boot.web.server.reactive.context.AnnotationConfigReactiveWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractSpringBridge {

    private var context: ConfigurableApplicationContext? = null
    private val readyLatch = CountDownLatch(1)

    protected abstract fun log(message: String)
    protected abstract fun logError(message: String, e: Exception?)
    protected abstract fun getDataFolderPath(): String

    fun start(config: PluginConfig) {
        val pluginClassLoader = this::class.java.classLoader

        val springThread = Thread({
            try {
                Thread.currentThread().contextClassLoader = pluginClassLoader

                val app = SpringApplication(MineGrafanaSpringConfig::class.java)
                app.setWebApplicationType(WebApplicationType.REACTIVE)
                app.setBannerMode(Banner.Mode.OFF)
                app.setApplicationContextFactory { _ ->
                    AnnotationConfigReactiveWebServerApplicationContext()
                }
                app.setDefaultProperties(mapOf(
                    "server.port" to config.web.port.toString(),
                    "server.address" to config.web.bindAddress,
                    "minegrafana.server-id" to config.serverId,
                    "minegrafana.server-type" to config.serverType,
                    "minegrafana.data-folder" to getDataFolderPath(),
                    "minegrafana.metrics.influx.enabled" to config.metrics.influx.enabled.toString(),
                    "minegrafana.metrics.influx.url" to config.metrics.influx.url,
                    "minegrafana.metrics.influx.org" to config.metrics.influx.org,
                    "minegrafana.metrics.influx.bucket" to config.metrics.influx.bucket,
                    "minegrafana.metrics.influx.token" to config.metrics.influx.token,
                    "minegrafana.metrics.influx.step-seconds" to config.metrics.influx.stepSeconds.toString(),
                    "spring.main.lazy-initialization" to "true"
                ))

                context = app.run()
                log("Spring context type: ${context!!.javaClass.name}")
                log("Spring Boot started on port ${config.web.port}")
                readyLatch.countDown()
            } catch (e: Exception) {
                logError("Failed to start Spring Boot: ${e.message}", e)
                readyLatch.countDown()
            }
        }, "minegrafana-spring")

        springThread.isDaemon = true
        springThread.contextClassLoader = pluginClassLoader
        springThread.start()
    }

    fun stop() {
        try {
            context?.close()
            log("Spring Boot context stopped.")
        } catch (e: Exception) {
            logError("Error stopping Spring Boot: ${e.message}", null)
        }
    }

    fun isReady(): Boolean = context?.isRunning == true

    fun awaitReady(timeoutSeconds: Long = 30): Boolean {
        return readyLatch.await(timeoutSeconds, TimeUnit.SECONDS) && isReady()
    }

    fun <T : Any> getBean(clazz: Class<T>): T? {
        return context?.let { ctx ->
            try {
                ctx.beanFactory.getBean(clazz)
            } catch (_: org.springframework.beans.factory.NoSuchBeanDefinitionException) {
                null // Expected — optional bean not registered
            } catch (e: Exception) {
                logError("Failed to get bean ${clazz.simpleName}: ${e.message}", e)
                null
            }
        }
    }
}
