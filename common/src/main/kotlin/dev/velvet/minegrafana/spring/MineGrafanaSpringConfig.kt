package dev.velvet.minegrafana.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["dev.velvet.minegrafana"])
@EnableScheduling
class MineGrafanaSpringConfig
