import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.shadow)
    kotlin("kapt")
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.velocity.api)
    kapt(libs.velocity.api)
    testImplementation(kotlin("test"))
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("mineGrafana-velocity")

    relocate("com.fasterxml.jackson", "dev.velvet.minegrafana.libs.jackson")
    relocate("org.yaml.snakeyaml", "dev.velvet.minegrafana.libs.snakeyaml")
    relocate("io.netty", "dev.velvet.minegrafana.libs.netty")
    relocate("reactor.netty", "dev.velvet.minegrafana.libs.reactor.netty")

    // Velocity has no MavenLibraryResolver — Kotlin must be bundled in the JAR
    exclude("META-INF/maven/**")
    exclude("META-INF/native-image/**")

    mergeServiceFiles()

    transform(ServiceFileTransformer::class.java) {
        path = "META-INF/spring"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
