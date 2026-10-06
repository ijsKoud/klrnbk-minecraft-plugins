package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogAction
import nl.klrnbk.minecraft.plugins.discordId.common.services.audit.AuditLogService
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.uuid.Uuid

@Singleton
class AdminCommandsFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val botMain: BotMain,
        private val dataTransferService: DataTransferService,
        private val auditLogService: AuditLogService,
    ) {
        /**
         * @param actorPlayerId the Minecraft UUID of the sender of the command, null for the console.
         */
        fun reload(
            dataDirectory: Path,
            actorPlayerId: Uuid?,
        ) {
            logger.info("Reloading plugin...")
            val config = configService.load(dataDirectory)

            databaseService.restart(config.database, dataDirectory)
            botMain.stop()
            botMain.start()
            auditLogService.log(AuditLogAction.RELOAD, auditLogService.actorIdentityId(actorPlayerId))
            logger.info("Plugin is reloaded.")
        }

        fun exportData(
            dataDirectory: Path,
            actorPlayerId: Uuid?,
        ): TextComponent =
            try {
                val outcome = dataTransferService.exportData(dataDirectory)
                auditLogService.log(AuditLogAction.EXPORT, auditLogService.actorIdentityId(actorPlayerId), details = outcome.fileName)
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(
                        LanguageKeys.LINK_CODE_EXPORT_SUCCESS,
                        Component.text(outcome.fileName),
                        Component.text(outcome.result.totalRows),
                    ).build()
            } catch (exception: DatabaseTransferException) {
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(LanguageKeys.LINK_CODE_TRANSFER_FAILED, Component.text(exception.message.orEmpty()))
                    .build()
            }

        fun importData(
            dataDirectory: Path,
            fileName: String,
            actorPlayerId: Uuid?,
        ) = try {
            val result = dataTransferService.importData(dataDirectory, fileName)
            auditLogService.log(AuditLogAction.IMPORT, auditLogService.actorIdentityId(actorPlayerId), details = fileName)
            MessageFactory
                .factory()
                .appendAndParseWithTranslatable(
                    LanguageKeys.LINK_CODE_IMPORT_SUCCESS,
                    Component.text(result.totalRows),
                ).build()
        } catch (exception: DatabaseTransferException) {
            MessageFactory
                .factory()
                .appendAndParseWithTranslatable(
                    LanguageKeys.LINK_CODE_TRANSFER_FAILED,
                    Component.text(exception.message.orEmpty()),
                ).build()
        }
    }
