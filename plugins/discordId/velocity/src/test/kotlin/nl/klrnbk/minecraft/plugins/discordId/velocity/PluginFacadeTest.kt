package nl.klrnbk.minecraft.plugins.discordId.velocity

import com.google.inject.Guice
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.mockk
import io.mockk.verify
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.translation.GlobalTranslator
import nl.klrnbk.minecraft.packages.velocity.commands.services.CommandRegistryService
import nl.klrnbk.minecraft.plugins.discordId.common.DiscordIdTestEnvironment
import nl.klrnbk.minecraft.plugins.discordId.common.RecordingBotMain
import nl.klrnbk.minecraft.plugins.discordId.common.identityPlayer
import nl.klrnbk.minecraft.plugins.discordId.common.registerIdentity
import nl.klrnbk.minecraft.plugins.discordId.velocity.commands.DiscordIdCommand
import nl.klrnbk.minecraft.plugins.discordId.velocity.facades.PluginFacade
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.util.Locale

/**
 * The real plugin facade on a real data directory (so the bundled default config.yml is exercised).
 * Velocity has no test framework, so ProxyServer is mocked; the Discord bot is replaced by a recording
 * stand-in because starting it would log in to Discord.
 */
class PluginFacadeTest {
    private val env = DiscordIdTestEnvironment()
    private val pluginDirectory = Files.createTempDirectory("discord-id-velocity")
    private val server = mockk<ProxyServer>(relaxed = true)
    private val plugin = VelocityPlugin(NOPLogger.NOP_LOGGER, server, pluginDirectory)

    init {
        registerIdentity(identityPlayer("Alice"))
    }

    private val botMain get() = env.botMain as RecordingBotMain

    private fun facade(): PluginFacade {
        val command = DiscordIdCommand(env.adminCommandsFacade, env.linkFacade, pluginDirectory)
        val registry = CommandRegistryService(setOf(command), server, NOPLogger.NOP_LOGGER)
        return PluginFacade(env.configService, env.databaseService, botMain, registry, NOPLogger.NOP_LOGGER)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.databaseService.stop()
        env.dataDirectory.toFile().deleteRecursively()
        pluginDirectory.toFile().deleteRecursively()
    }

    @Test
    fun `starting creates the default config, connects the database, starts the bot and registers the command`() {
        facade().start(plugin)

        assertTrue(Files.exists(pluginDirectory.resolve("config.yml")))
        assertTrue(env.datasourceProvider.isConnected())
        assertTrue(Files.exists(pluginDirectory.resolve("database.db")))
        assertEquals(1, botMain.starts)
        verify { server.commandManager.register(any<CommandMeta>(), any<BrigadierCommand>()) }
    }

    @Test
    fun `starting loads the language file under the plugin namespace`() {
        facade().start(plugin)

        val rendered = GlobalTranslator.render(Component.translatable("discord-id.unlink.success"), Locale.US)

        assertTrue(plainText(rendered).contains("Successfully unlinked"), "rendered: $rendered")
    }

    @Test
    fun `the default config has the expected values`() {
        facade().start(plugin)

        val config = env.configService.getConfig()

        assertTrue(config.useProxy)
        assertEquals("YOUR_BOT_TOKEN_HERE", config.discord.botToken)
        assertEquals(2592000000L, config.discord.unlinkCooldown)
        assertTrue(config.logs.enabled)
        assertEquals(90, config.logs.purgeLogsAfterDays)
    }

    @Test
    fun `stopping stops the bot and disconnects the database`() {
        val facade = facade()
        facade.start(plugin)

        facade.stop()

        assertEquals(1, botMain.stops)
        assertFalse(env.datasourceProvider.isConnected())
    }

    @Test
    fun `an unreachable database does not stop the plugin from starting`() {
        Files.writeString(
            pluginDirectory.resolve("config.yml"),
            """
            discord:
              bot-token: token
            database:
              type: MYSQL
              host: 127.0.0.1
              port: 1
              database: nothing
            """.trimIndent(),
        )

        facade().start(plugin)

        assertFalse(env.datasourceProvider.isConnected())
        assertEquals(1, botMain.starts)
        verify { server.commandManager.register(any<CommandMeta>(), any<BrigadierCommand>()) }
    }

    @Test
    fun `the module wires the whole object graph`() {
        // Guice builds PluginFacade with every dependency; a missing binding fails here instead of on a server.
        val injector = Guice.createInjector(PluginModule(NOPLogger.NOP_LOGGER, server, pluginDirectory))

        assertTrue(injector.getInstance(PluginFacade::class.java) is PluginFacade)
    }

    private fun plainText(component: Component): String =
        (component as? TextComponent)?.content().orEmpty() + component.children().joinToString("") { plainText(it) }
}
