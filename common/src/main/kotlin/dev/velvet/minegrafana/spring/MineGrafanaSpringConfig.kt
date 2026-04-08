package dev.velvet.minegrafana.spring

import dev.velvet.minegrafana.monitoring.domain.service.HealthAssessor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["dev.velvet.minegrafana"])
@EnableScheduling
class MineGrafanaSpringConfig {

    @Bean
    fun healthAssessor(): HealthAssessor = HealthAssessor()
}
