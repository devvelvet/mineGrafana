plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    // Spring Boot BOM
    api(platform(libs.spring.boot.bom))

    // Spring
    api(libs.spring.boot.starter.webflux)
    api(libs.spring.boot.starter.actuator)
    api(libs.spring.boot.autoconfigure)
    // Micrometer
    api(libs.micrometer.prometheus)
    api(libs.micrometer.influx)

    // Jackson
    api(libs.jackson.module.kotlin)
    api(libs.jackson.dataformat.yaml)

    // Minecraft (compileOnly - provided by platform)
    compileOnly(libs.adventure.api)

    // Test
    testImplementation(kotlin("test"))
}
