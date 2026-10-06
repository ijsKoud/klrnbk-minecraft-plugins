package nl.klrnbk.minecraft.plugins.discordId.common

import nl.klrnbk.minecraft.packages.config.yaml.YamlConfigStore
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import nl.klrnbk.minecraft.packages.database.DatasourceConfig
import nl.klrnbk.minecraft.packages.database.DatasourceType
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransfer
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LinkCommand
import nl.klrnbk.minecraft.plugins.discordId.common.bot.commands.LookupCommand
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.InteractionEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.ReadyEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.RoleChangeEvent
import nl.klrnbk.minecraft.plugins.discordId.common.bot.events.UserRenameEvent
import nl.klrnbk.minecraft.plugins.discordId.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.discordId.common.facades.LinkFacade
import nl.klrnbk.minecraft.plugins.discordId.common.facades.ScheduledTasksFacade
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.ConfigProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models.DiscordIdPluginConfig
import nl.klrnbk.minecraft.plugins.discordId.common.providers.config.models.DiscordIdPluginDiscordConfig
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.DatasourceProvider
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.PlayerDiscordLinkCodeEntityRepository
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.repositories.PlayerDiscordLinkEntityRepository
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkCodeService
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.PlayerLinkService
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkCodeEntity
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkEntity
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * The real services wired together on top of a temporary SQLite database.
 *
 * The Discord bot is only constructed, never started: [BotMain.start] would log in to Discord.
 * [LinkFacade] reads the `IdentityProvider` when it is created, so register an identity (see [registerIdentity])
 * before touching [linkFacade] or anything built on it.
 */
class DiscordIdTestEnvironment(
    boosterRole: String? = null,
    unlinkCooldownMillis: Long = DiscordIdPluginDiscordConfig().unlinkCooldown,
    useProxy: Boolean = false,
) {
    val dataDirectory: Path = Files.createTempDirectory("discord-id-test")
    val datasourceConfig = DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db")

    // The classes are final, so the real config provider is used on top of a config file instead of a mock.
    val configProvider =
        ConfigProvider(YamlConfigStore()).also { provider ->
            provider.save(
                dataDirectory,
                DiscordIdPluginConfig(
                    useProxy = useProxy,
                    database = datasourceConfig,
                    discord =
                        DiscordIdPluginDiscordConfig(
                            boosterRole = boosterRole,
                            unlinkCooldown = unlinkCooldownMillis,
                            botToken = "test-token",
                        ),
                ),
            )
            provider.load(dataDirectory)
        }
    val configService = ConfigService(configProvider)

    val context = DatabaseContext()
    val datasourceProvider = DatasourceProvider(context)
    val linkRepository = PlayerDiscordLinkEntityRepository(context)
    val codeRepository = PlayerDiscordLinkCodeEntityRepository(context)
    val databaseService = DatabaseService(datasourceProvider, codeRepository, NOPLogger.NOP_LOGGER)
    val linkService = PlayerLinkService(linkRepository, configProvider)
    val codeService = PlayerLinkCodeService(codeRepository)
    val dataTransferService = DataTransferService(NOPLogger.NOP_LOGGER, DatabaseTransfer(context))
    val scheduledTasksFacade = ScheduledTasksFacade(configService, linkService, databaseService, NOPLogger.NOP_LOGGER)

    val linkFacade by lazy { LinkFacade(linkService, codeService) }
    val linkCommand by lazy { LinkCommand(linkFacade, configService, NOPLogger.NOP_LOGGER) }
    val lookupCommand by lazy { LookupCommand(linkFacade, NOPLogger.NOP_LOGGER) }
    val readyEvent by lazy { ReadyEvent(NOPLogger.NOP_LOGGER, lookupCommand, linkCommand, configService) }
    val interactionEvent by lazy { InteractionEvent(lookupCommand, linkCommand) }
    val userRenameEvent by lazy { UserRenameEvent(linkFacade) }
    val roleChangeEvent by lazy { RoleChangeEvent(linkFacade, configService) }
    val botMain by lazy {
        RecordingBotMain(
            configService,
            NOPLogger.NOP_LOGGER,
            readyEvent,
            interactionEvent,
            userRenameEvent,
            roleChangeEvent,
            scheduledTasksFacade,
        )
    }
    val adminCommandsFacade by lazy {
        AdminCommandsFacade(NOPLogger.NOP_LOGGER, configService, databaseService, botMain, dataTransferService)
    }

    /**
     * Pretends the link was last changed [by] ago, to get past (or stay inside) the unlink cooldown.
     */
    fun ageLink(
        identityId: Uuid,
        by: Duration,
    ) = transaction(context.database) {
        checkNotNull(PlayerDiscordLinkEntity.findById(identityId)).lastUpdatedAt = Clock.System.now() - by
    }

    fun expireCode(identityId: Uuid) =
        transaction(context.database) {
            checkNotNull(PlayerDiscordLinkCodeEntity.findById(identityId)).validUntil = Clock.System.now() - 1.minutes
        }

    fun start() {
        databaseService.start(datasourceConfig, dataDirectory)
    }

    fun close() {
        databaseService.stop()
        dataDirectory.toFile().deleteRecursively()
    }
}
