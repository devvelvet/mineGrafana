package dev.velvet.minegrafana.shared.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

object ConfigLoader {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory()).apply {
        registerKotlinModule()
        propertyNamingStrategy = PropertyNamingStrategies.KEBAB_CASE
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    /**
     * @param defaultResourcePath Default config path in classpath (e.g., "config-default.yml").
     *        Each module can use a different default config.
     */
    fun loadConfig(dataFolder: Path, defaultResourcePath: String = "config-default.yml"): PluginConfig {
        val configFile = dataFolder.resolve("config.yml")
        if (!Files.exists(configFile)) {
            saveDefaultFromResource(dataFolder, configFile, defaultResourcePath)
        }
        return Files.newInputStream(configFile).use { input ->
            mapper.readValue(input, PluginConfig::class.java)
        }
    }

    fun loadMessages(dataFolder: Path): MessagesConfig {
        val messagesFile = dataFolder.resolve("messages.yml")
        if (!Files.exists(messagesFile)) {
            Files.createDirectories(dataFolder)
            mapper.writeValue(messagesFile.toFile(), MessagesConfig())
        }
        return Files.newInputStream(messagesFile).use { input ->
            mapper.readValue(input, MessagesConfig::class.java)
        }
    }

    fun <T> load(inputStream: InputStream, clazz: Class<T>): T {
        return mapper.readValue(inputStream, clazz)
    }

    private fun saveDefaultFromResource(dataFolder: Path, configFile: Path, resourcePath: String) {
        Files.createDirectories(dataFolder)
        // Copy default config from classpath
        val stream = ConfigLoader::class.java.classLoader.getResourceAsStream(resourcePath)
        if (stream != null) {
            stream.use { Files.copy(it, configFile) }
        } else {
            // Serialize defaults if resource not found
            mapper.writeValue(configFile.toFile(), PluginConfig())
        }
    }
}
