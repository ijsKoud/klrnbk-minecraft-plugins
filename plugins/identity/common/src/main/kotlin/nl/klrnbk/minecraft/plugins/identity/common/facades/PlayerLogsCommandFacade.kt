package nl.klrnbk.minecraft.plugins.identity.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import nl.klrnbk.minecraft.packages.common.constants.BRANDING_PRIMARY_COLOUR
import nl.klrnbk.minecraft.packages.common.constants.COLOUR_GRAY
import nl.klrnbk.minecraft.packages.common.constants.COLOUR_WHITE
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogs
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.facades.PlayerlistCommandFacade.Companion.ITEMS_PER_PAGE
import nl.klrnbk.minecraft.plugins.identity.common.services.config.ConfigService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.pkgs.i18n.utils.instantToComponentText
import kotlin.math.ceil
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerLogsCommandFacade
    @Inject
    constructor(
        private val playerDetailsService: PlayerDetailsService,
        private val playerConnectionLogsService: PlayerConnectionLogsService,
        private val configService: ConfigService,
    ) {
        fun getPlayerLogs(
            playerName: String,
            page: Int,
        ): PlayerLogsResult? {
            val config = configService.getConfig()
            val player = playerDetailsService.getPlayerDetailsByName(playerName) ?: return null
            val logs = playerConnectionLogsService.getLogsForPlayer(player.id.toKotlinUuid(), page, ITEMS_PER_PAGE, config.encryptionKey!!)

            val totalLogsCount = playerConnectionLogsService.getLogsCountForPlayer(player.id.toKotlinUuid())
            val totalPages = ceil(totalLogsCount.toDouble() / ITEMS_PER_PAGE).toInt()

            return PlayerLogsResult(
                player = player,
                logs = logs,
                currentPage = page.toString(),
                totalPages = totalPages.toString(),
                nextPage = if (page < totalPages) (page + 1).toString() else totalPages.toString(),
                previousPage = if (page > 1) (page - 1).toString() else "1",
            )
        }

        fun getPlayerNameSuggestions(): List<String> = playerDetailsService.getAllPlayerNames()

        fun produceMessage(
            result: PlayerLogsResult,
            canSeePlayerIps: Boolean,
        ): TextComponent {
            val factory = MessageFactory.factory()
            val message =
                factory
                    .appendAndParseWithTranslatable(LanguageKeys.PLAYER_LOGS_HEADER) {
                        it.decorate(TextDecoration.BOLD)
                    }.appendNewlines(2)
                    .appendWithComponent(
                        Component
                            .text()
                            .append(
                                Component
                                    .translatable(
                                        LanguageKeys.PLAYER_NAME,
                                        TextColor.fromHexString(
                                            BRANDING_PRIMARY_COLOUR,
                                        ),
                                    ),
                            ).append(Component.text(" = "))
                            .append(Component.text(result.player.name))
                            .appendNewline()
                            .build(),
                    ).appendNewlines(2)
                    // Entries
                    .appendEntries(result.logs) {
                        Component
                            .text()
                            .append(
                                instantToComponentText(factory.miniMessage, it.timestamp),
                            ).appendNewline()
                            .append(
                                Component
                                    .translatable(
                                        LanguageKeys.PLAYER_LOGS_ACTION,
                                        TextColor.fromHexString(
                                            BRANDING_PRIMARY_COLOUR,
                                        ),
                                    ),
                            ).append(Component.text(" = "))
                            .append(Component.translatable("identity.player.common.${it.action.name.lowercase()}"))
                            .appendNewline()
                            .append(
                                Component
                                    .translatable(
                                        LanguageKeys.PLAYER_LOGS_IP_ADDRESS,
                                        TextColor.fromHexString(
                                            BRANDING_PRIMARY_COLOUR,
                                        ),
                                    ),
                            ).append(Component.text(" = "))
                            .append(
                                if (canSeePlayerIps) {
                                    Component.text(
                                        it.ip ?: "...",
                                        TextColor.fromHexString(
                                            COLOUR_GRAY,
                                        ),
                                    )
                                } else {
                                    Component.translatable(
                                        LanguageKeys.PLAYER_UNAVAILABLE,
                                        TextColor.fromHexString(
                                            COLOUR_GRAY,
                                        ),
                                    )
                                },
                            ).color(TextColor.fromHexString(COLOUR_WHITE))
                            .appendNewline()
                            .append(
                                Component
                                    .translatable(
                                        LanguageKeys.PLAYER_LOGS_SERVER,
                                        TextColor.fromHexString(
                                            BRANDING_PRIMARY_COLOUR,
                                        ),
                                    ),
                            ).append(Component.text(" = "))
                            .append(Component.text(it.server.name))
                            .appendSpace()
                            .append(
                                Component.text(
                                    it.server.ip,
                                    TextColor.fromHexString(
                                        COLOUR_GRAY,
                                    ),
                                ),
                            ).appendNewline()
                            .appendNewline()
                            .build()
                    }
                    // PAGES
                    .appendAndParseWithTranslatable(
                        LanguageKeys.PLAYER_LIST_PAGE,
                        Component.text(result.currentPage),
                        Component.text(result.totalPages),
                    ).appendNewlines(1)
                    .appendWithComponent(
                        Component
                            .translatable(LanguageKeys.PLAYER_LIST_FOOTER_PREVIOUS)
                            .clickEvent(ClickEvent.runCommand("/playerlogs ${result.player.name} ${result.previousPage}")),
                    ).appendText(" / ")
                    .appendWithComponent(
                        Component
                            .translatable(LanguageKeys.PLAYER_LIST_FOOTER_NEXT)
                            .clickEvent(ClickEvent.runCommand("/playerlogs ${result.player.name} ${result.nextPage}")),
                    )

            return message.build()
        }

        data class PlayerLogsResult(
            val player: IdentityPlayer,
            val logs: List<IdentityPlayerLogs>,
            val nextPage: String,
            val previousPage: String,
            val totalPages: String,
            val currentPage: String,
        )
    }
