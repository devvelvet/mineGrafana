package dev.velvet.minegrafana.monitoring.infrastructure.grafana

import dev.velvet.minegrafana.shared.config.GrafanaConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class GrafanaProvisioner {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * @param serverType "paper" or "velocity" — determines which dashboard to import
     */
    fun provision(config: GrafanaConfig, scrapeUrl: String, serverType: String = "paper") {
        if (!config.autoProvision) return
        if (config.apiKey.isBlank()) {
            logger.warn("[Grafana] auto-provision enabled but api-key is empty. Skipping.")
            return
        }

        logger.info("[Grafana] Auto-provisioning to ${config.url} (type: $serverType)...")

        val client = WebClient.builder()
            .baseUrl(config.url)
            .defaultHeader("Authorization", "Bearer ${config.apiKey}")
            .defaultHeader("Content-Type", "application/json")
            .build()

        createDatasource(client, config)

        // Import dashboard based on server type
        val dashboardFile = when (serverType) {
            "velocity" -> "grafana/velocity-overview.json"
            else -> "grafana/server-overview.json"
        }
        importDashboard(client, config.datasourceName, dashboardFile)
    }

    private fun createDatasource(client: WebClient, config: GrafanaConfig) {
        val body = mapOf(
            "name" to config.datasourceName,
            "type" to "prometheus",
            "url" to config.prometheusUrl,
            "access" to "proxy",
            "isDefault" to true
        )

        client.post()
            .uri("/api/datasources")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .onErrorResume { e ->
                logger.info("[Grafana] Datasource '${config.datasourceName}': ${e.message?.take(80) ?: "exists"}")
                Mono.empty()
            }
            .doOnNext { logger.info("[Grafana] Datasource '${config.datasourceName}' created.") }
            .subscribe()
    }

    private fun importDashboard(client: WebClient, datasourceName: String, resourcePath: String) {
        try {
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            if (stream == null) {
                logger.warn("[Grafana] $resourcePath not found in classpath")
                return
            }

            val dashboardJson = stream.bufferedReader().use { it.readText() }

            val importPayload = """
            {
                "dashboard": $dashboardJson,
                "overwrite": true,
                "inputs": [{
                    "name": "DS_PROMETHEUS",
                    "type": "datasource",
                    "pluginId": "prometheus",
                    "value": "$datasourceName"
                }]
            }
            """.trimIndent()

            client.post()
                .uri("/api/dashboards/import")
                .bodyValue(importPayload)
                .retrieve()
                .bodyToMono(String::class.java)
                .onErrorResume { e ->
                    logger.warn("[Grafana] Dashboard import: ${e.message?.take(100)}")
                    Mono.empty()
                }
                .doOnNext { logger.info("[Grafana] Dashboard '$resourcePath' imported.") }
                .subscribe()
        } catch (e: Exception) {
            logger.warn("[Grafana] Failed to import dashboard: ${e.message}")
        }
    }
}
