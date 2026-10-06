package nl.klrnbk.minecraft.plugins.discordId.paper

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.PlayerDiscordLinkCodeEntityRepository
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkService
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.Uuid
import nl.klrnbk.minecraft.plugins.discordId.common.DiscordIdTestEnvironment
import nl.klrnbk.minecraft.plugins.discordId.common.RecordingBotMain
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.paper.facades.PluginFacade
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.slf4j.helpers.NOPLogger
import java.io.File

/**
 * A MockBukkit server with the plugin loaded.
 *
 * Loading goes through [MockBukkit.load], i.e. the real plugin class and its onEnable. The default config has
 * `use-proxy: false`, which would start the Discord bot (a login to Discord), so the config is written first:
 * [load] enables the plugin in proxy mode (no bot, no commands). [startFully] then starts the facade the way a
 * server without a proxy would, with a [RecordingBotMain] instead of the real bot.
 */
class PaperTestHarness {
    val server: ServerMock = MockBukkit.mock()
    val env = DiscordIdTestEnvironment()

    // MockBukkit names the data folder "<name>-<version>"; the version comes from the processed paper-plugin.yml.
    private val version =
        Regex("""version:\s*'?([^'\s]+)'?""")
            .find(checkNotNull(javaClass.classLoader.getResource("paper-plugin.yml")).readText())!!
            .groupValues[1]
    val dataFolder = File(server.pluginsFolder, "KLRNBK-DiscordId-$version")
    lateinit var plugin: MCPluginMain
        private set
    var facade: PluginFacade? = null
        private set
    lateinit var injector: Injector
        private set

    // The services of the running plugin, on the plugin's own database.
    val linkService get() = injector.getInstance(PlayerLinkService::class.java)
    val codeRepository get() = injector.getInstance(PlayerDiscordLinkCodeEntityRepository::class.java)

    fun ageLink(
        identityId: Uuid,
        by: Duration,
    ) = transaction(injector.getInstance(DatabaseContext::class.java).database) {
        checkNotNull(PlayerDiscordLinkEntity.findById(identityId)).lastUpdatedAt = Clock.System.now() - by
    }

    val botMain get() = env.botMain as RecordingBotMain

    fun writeConfig(
        useProxy: Boolean,
        database: String = "type: SQLITE\n  database: database.db",
    ) {
        dataFolder.mkdirs()
        File(dataFolder, "config.yml").writeText(
            """
            use-proxy: $useProxy
            discord:
              bot-token: test-token
            database:
              $database
            """.trimIndent(),
        )
    }

    fun load(useProxy: Boolean = true): MCPluginMain {
        writeConfig(useProxy)
        plugin = MockBukkit.load(MCPluginMain::class.java)
        return plugin
    }

    /**
     * Restarts the plugin without proxy mode and with the recording bot.
     */
    fun startFully(): PluginFacade {
        plugin.onDisable()
        writeConfig(useProxy = false)

        injector =
            Guice.createInjector(
                PluginModule(plugin, NOPLogger.NOP_LOGGER, server),
                object : AbstractModule() {
                    override fun configure() {
                        bind(BotMain::class.java).toInstance(botMain)
                    }
                },
            )
        return injector.getInstance(PluginFacade::class.java).also {
            it.start(plugin)
            facade = it
        }
    }

    fun close() {
        try {
            facade?.stop()
        } finally {
            IdentityProvider.unregister()
            MockBukkit.unmock()
            env.close()
            dataFolder.deleteRecursively()
        }
    }
}
