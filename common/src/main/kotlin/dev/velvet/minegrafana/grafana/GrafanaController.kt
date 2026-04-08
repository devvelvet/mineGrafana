package dev.velvet.minegrafana.grafana

import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class GrafanaController {

    private val availableDashboards = listOf("server-overview", "velocity-overview")

    /** Grafana JSON download endpoint */
    @GetMapping("/api/v1/grafana/dashboards/{name}")
    @ResponseBody
    fun downloadDashboard(@PathVariable name: String): ResponseEntity<ByteArray> {
        if (name !in availableDashboards) {
            return ResponseEntity.notFound().build()
        }

        val resource = ClassPathResource("grafana/$name.json")
        if (!resource.exists()) {
            return ResponseEntity.notFound().build()
        }

        val content = resource.inputStream.readAllBytes()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$name.json")
            .contentType(MediaType.APPLICATION_JSON)
            .body(content)
    }

    /** Available dashboard list */
    @GetMapping("/api/v1/grafana/dashboards")
    @ResponseBody
    fun listDashboards(): ResponseEntity<List<Map<String, String>>> {
        return ResponseEntity.ok(availableDashboards.map { mapOf("name" to it, "url" to "/api/v1/grafana/dashboards/$it") })
    }
}
