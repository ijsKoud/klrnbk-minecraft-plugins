package nl.klrnbk.minecraft.plugins.identity.velocity

import com.google.inject.Injector
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.util.UuidUtils
import io.mockk.every
import io.mockk.mockk
import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.packages.velocity.commands.services.CommandRegistryService
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerLogsCommandFacade
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.config.models.IdentityPluginConfig
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerConnectionLogEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.repositories.PlayerEntityRepository
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import nl.klrnbk.minecraft.plugins.identity.velocity.facades.PluginFacade
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Optional
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** Identity registers on LoginEvent, so the UUID is the final one whatever the login mode or auth plugin decided. */
class LoginRegistrationTest {
    private val directory = Files.createTempDirectory("identity-login")
    private val dbConfig = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db")
    private val context = DatabaseContext()
    private val configService =
        ConfigService(ConfigProvider(YamlConfigStore())).also {
            ConfigProvider(YamlConfigStore()).save(
                directory,
                IdentityPluginConfig(encryptionKey = CryptographyUtil.getRandomSecretKey(), database = dbConfig),
            )
            it.load(directory)
        }
    private val playerRepo = PlayerEntityRepository(context)
    private val logRepo = PlayerConnectionLogEntityRepository(context)
    private val databaseService = DatabaseService(DatasourceProvider(context), logRepo, NOPLogger.NOP_LOGGER)
    private val details =
        PlayerDetailsService(
            playerRepo,
            object : PlayerOnlineStatusProvider {
                override fun isPlayerOnline(playerId: Uuid) = false
            },
            NOPLogger.NOP_LOGGER,
        )
    private val logsFacade =
        PlayerLogsCommandFacade(details, PlayerConnectionLogsService(playerRepo, logRepo, NOPLogger.NOP_LOGGER), configService)
    private val plugin: VelocityPlugin

    init {
        databaseService.start(dbConfig, directory)
        val unsafe =
            Unsafe::class.java
                .getDeclaredField("theUnsafe")
                .also { it.isAccessible = true }
                .get(null) as Unsafe
        val facade =
            PluginFacade(
                configService,
                databaseService,
                details,
                PlayerConnectionLogsService(playerRepo, logRepo, NOPLogger.NOP_LOGGER),
                unsafe.allocateInstance(CommandRegistryService::class.java) as CommandRegistryService,
                NOPLogger.NOP_LOGGER,
            )
        val injector =
            Proxy.newProxyInstance(Injector::class.java.classLoader, arrayOf(Injector::class.java)) { _, method, args ->
                if (method.name == "getInstance" && args[0] == PluginFacade::class.java) facade else null
            } as Injector
        plugin = unsafe.allocateInstance(VelocityPlugin::class.java) as VelocityPlugin
        VelocityPlugin::class.java
            .getDeclaredField("injector")
            .also { it.isAccessible = true }
            .set(plugin, injector)
    }

    @AfterEach
    fun cleanup() {
        databaseService.stop()
        directory.toFile().deleteRecursively()
    }

    private fun player(
        name: String,
        uuid: UUID,
    ): Player =
        mockk<Player>(relaxed = true).also {
            every { it.uniqueId } returns uuid
            every { it.username } returns name
            every { it.remoteAddress } returns InetSocketAddress("127.0.0.1", 25565)
            every { it.virtualHost } returns Optional.empty()
        }

    private fun disconnect(player: Player) = plugin.onDisconnectEvent(DisconnectEvent(player, DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN))

    @Test
    fun `a cracked player is registered under the offline uuid and playerlogs shows connect and disconnect`() {
        val steve = player("Steve", UuidUtils.generateOfflinePlayerUuid("Steve"))

        plugin.onLoginEvent(LoginEvent(steve, ""))
        disconnect(steve)

        assertNotNull(details.getPlayerDetailsByPlayerId(steve.uniqueId.toKotlinUuid()))
        assertEquals(2, logsFacade.getPlayerLogs("Steve", 1)!!.logs.size)
    }

    @Test
    fun `joining twice keeps a single identity`() {
        val steve = player("Steve", UuidUtils.generateOfflinePlayerUuid("Steve"))

        plugin.onLoginEvent(LoginEvent(steve, ""))
        plugin.onLoginEvent(LoginEvent(steve, ""))

        assertEquals(1, details.getTotalPlayerCount())
    }

    @Test
    fun `a disconnect of a player that was never registered is ignored`() {
        disconnect(player("Ghost", UUID.randomUUID()))

        assertNull(logsFacade.getPlayerLogs("Ghost", 1))
    }
}
