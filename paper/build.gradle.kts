import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.paper.api)
    testImplementation(kotlin("test"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("mineGrafana-paper")

    relocate("com.fasterxml.jackson", "dev.velvet.minegrafana.libs.jackson")
    relocate("org.yaml.snakeyaml", "dev.velvet.minegrafana.libs.snakeyaml")
    relocate("io.netty", "dev.velvet.minegrafana.libs.netty")
    relocate("reactor.netty", "dev.velvet.minegrafana.libs.reactor.netty")

    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("org/jetbrains/kotlin/**")
    exclude("org/jetbrains/annotations/**")
    exclude("META-INF/kotlin-stdlib*.kotlin_module")
    exclude("META-INF/maven/**")
    exclude("META-INF/native-image/**")

    // Merge META-INF/services (default)
    mergeServiceFiles()

    // Merge META-INF/spring/*.imports files (Spring Boot auto-configuration)
    // Merge duplicate META-INF/spring/* files (Spring Boot auto-config)
    transform(ServiceFileTransformer::class.java) {
        path = "META-INF/spring"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
