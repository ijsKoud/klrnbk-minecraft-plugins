package nl.klrnbk.minecraft.plugins.discordId.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.plugins.discordId.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.discordId.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DataTransferService
import nl.klrnbk.minecraft.plugins.discordId.common.services.database.DatabaseService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import org.slf4j.Logger
import java.nio.file.Path

@Singleton
class AdminCommandsFacade
    @Inject
    constructor(
        private val logger: Logger,
        private val configService: ConfigService,
        private val databaseService: DatabaseService,
        private val dataTransferService: DataTransferService,
    ) {
        fun reload(dataDirectory: Path) {
            logger.info("Reloading plugin...")
            val config = configService.load(dataDirectory)

            databaseService.restart(config.database, dataDirectory)
            logger.info("Plugin is reloaded.")
        }

        fun exportData(dataDirectory: Path): TextComponent =
            try {
                val outcome = dataTransferService.exportData(dataDirectory)
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
        ) = try {
            val result = dataTransferService.importData(dataDirectory, fileName)
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
