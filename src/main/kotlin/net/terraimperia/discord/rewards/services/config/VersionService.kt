package net.terraimperia.discord.rewards.services.config

import com.google.inject.Singleton
import java.util.Properties

@Singleton
class VersionService {
    val version: String = load()

    private fun load(): String {
        val stream =
            checkNotNull(
                javaClass.getResourceAsStream("/plugin.properties"),
            ) {
                "plugin.properties was not found in the JAR"
            }

        val properties =
            Properties().apply {
                load(stream)
            }

        val version = properties.getProperty("version")
        return version
    }
}
