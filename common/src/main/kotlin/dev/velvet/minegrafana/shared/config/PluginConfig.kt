package dev.velvet.minegrafana.shared.config

data class PluginConfig(
    val serverId: String = "default",
    val serverType: String = "paper",
    val web: WebConfig = WebConfig(),
    val features: FeaturesConfig = FeaturesConfig(),
    val metrics: MetricsExportConfig = MetricsExportConfig(),
    val grafana: GrafanaConfig = GrafanaConfig()
)

data class GrafanaConfig(
    val autoProvision: Boolean = false,
    val url: String = "http://localhost:3000",
    val apiKey: String = "",
    val datasourceName: String = "mineGrafana-prometheus",
    val prometheusUrl: String = "http://localhost:9090"
)

data class WebConfig(
    val port: Int = 9100,
    val bindAddress: String = "0.0.0.0"
)

data class FeaturesConfig(
    val monitoring: MonitoringFeatureConfig = MonitoringFeatureConfig()
)

data class MonitoringFeatureConfig(
    val enabled: Boolean = true,
    val collectionIntervalSeconds: Int = 1,
    val entityCollectionIntervalSeconds: Int = 5,
    val profilerEnabled: Boolean = true
)

data class MetricsExportConfig(
    val prometheus: PrometheusExportConfig = PrometheusExportConfig(),
    val influx: InfluxExportConfig = InfluxExportConfig()
)

data class PrometheusExportConfig(
    val enabled: Boolean = true,
    val endpoint: String = "/metrics"
)

data class InfluxExportConfig(
    val enabled: Boolean = false,
    val url: String = "http://localhost:8086",
    val org: String = "minecraft",
    val bucket: String = "minegrafana",
    val token: String = "",
    val stepSeconds: Int = 10
)
