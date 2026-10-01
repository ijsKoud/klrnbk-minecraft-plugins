package nl.klrnbk.minecraft.packages.config.yaml

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.Singleton
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

@Singleton
class YamlConfigStore {
    private val mapper: ObjectMapper = defaultMapper()

    fun ensureDefaultFile(
        dataDirectory: Path,
        fileName: String = DEFAULT_FILE_NAME,
        defaultResourcePath: String = fileName,
        placeholders: Map<String, String>,
    ): Path {
        val configPath = dataDirectory.resolve(fileName)
        if (Files.exists(configPath)) {
            return configPath
        }

        Files.createDirectories(configPath.parent)

        val resource =
            javaClass.classLoader.getResourceAsStream(defaultResourcePath.removePrefix("/"))
                ?: throw FileNotFoundException(
                    "Resource '$defaultResourcePath' was not found.",
                )

        resource.use {
            val content = it.readBytes().toString(Charsets.UTF_8)
            val replacedContent =
                placeholders.entries.fold(content) { acc, (key, value) ->
                    acc.replace("{{$key}}", value)
                }

            Files.write(configPath, replacedContent.toByteArray(Charsets.UTF_8))
        }

        return configPath
    }

    fun <T : Any> read(
        path: Path,
        type: KClass<T>,
    ): T =
        Files.newInputStream(path).use { input ->
            mapper.readValue(input, type.java)
        }

    inline fun <reified T : Any> read(path: Path): T = read(path, T::class)

    fun write(
        path: Path,
        value: Any,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(path).use { output ->
            mapper.writeValue(output, value)
        }
    }

    inline fun <reified T : Any> loadOrCreate(
        dataDirectory: Path,
        fileName: String = DEFAULT_FILE_NAME,
        defaultResourcePath: String = fileName,
        placeholders: Map<String, String> = emptyMap(),
    ): T {
        val configPath =
            ensureDefaultFile(
                dataDirectory = dataDirectory,
                fileName = fileName,
                defaultResourcePath = defaultResourcePath,
                placeholders = placeholders,
            )
        return read(configPath)
    }

    companion object {
        const val DEFAULT_FILE_NAME = "config.yml"

        private fun defaultMapper(): ObjectMapper =
            JsonMapper
                .builder(YAMLFactory())
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build()
                .registerKotlinModule()
    }
}
