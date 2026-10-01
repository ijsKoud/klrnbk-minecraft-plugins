package nl.klrnbk.minecraft.plugins.identity.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import nl.klrnbk.minecraft.packages.common.constants.BRANDING_PRIMARY_COLOUR
import nl.klrnbk.minecraft.packages.common.constants.COLOUR_WHITE
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.PlayerConnectionLogsService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.pkgs.i18n.utils.instantToComponentText
import kotlin.time.toKotlinInstant
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerInformationCommandFacade
    @Inject
    constructor(
        private val playerDetailsService: PlayerDetailsService,
        private val playerConnectionLogsService: PlayerConnectionLogsService,
    ) {
        fun getPlayerInformation(playerName: String): PlayerInformationResult? {
            val player = playerDetailsService.getPlayerDetailsByName(playerName) ?: return null
            val logs = playerConnectionLogsService.getLogsCountForPlayer(player.id.toKotlinUuid())

            return PlayerInformationResult(
                player = player,
                hasLogs = logs > 0,
                logsCount = logs.toString(),
            )
        }

        fun getPlayerNameSuggestions(): List<String> = playerDetailsService.getAllPlayerNames()

        fun produceMessage(result: PlayerInformationResult): TextComponent {
            val factory = MessageFactory.factory()
            val message =
                factory
                    .appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_HEADER) {
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
                    ).appendWithComponent(
                        Component
                            .text()
                            .append(
                                Component.translatable(
                                    LanguageKeys.PLAYER_INFO_PLAYER_UUID,
                                    TextColor.fromHexString(
                                        BRANDING_PRIMARY_COLOUR,
                                    ),
                                ),
                            ).append(Component.text(" = "))
                            .append(Component.text(result.player.playerId.toString()))
                            .appendNewline()
                            .build(),
                    ).appendWithComponent(
                        Component
                            .text()
                            .append(
                                Component.translatable(
                                    LanguageKeys.PLAYER_INFO_INTERNAL_UUID,
                                    TextColor.fromHexString(
                                        BRANDING_PRIMARY_COLOUR,
                                    ),
                                ),
                            ).append(Component.text(" = "))
                            .append(Component.text(result.player.id.toString()))
                            .appendNewline()
                            .build(),
                    ).appendWithComponent(
                        Component
                            .text()
                            .append(
                                Component.translatable(
                                    LanguageKeys.PLAYER_INFO_FIRST_JOIN,
                                    TextColor.fromHexString(
                                        BRANDING_PRIMARY_COLOUR,
                                    ),
                                ),
                            ).append(Component.text(" = "))
                            .append(instantToComponentText(factory.miniMessage, result.player.firstJoined.toKotlinInstant()))
                            .appendNewline()
                            .build(),
                    ).appendWithComponent(
                        Component
                            .text()
                            .append(
                                Component.translatable(
                                    LanguageKeys.PLAYER_INFO_STATUS,
                                    TextColor.fromHexString(
                                        BRANDING_PRIMARY_COLOUR,
                                    ),
                                ),
                            ).append(Component.text(" = "))
                            .append(
                                Component.translatable(
                                    if (result.player.isPlayerOnline) LanguageKeys.PLAYER_ONLINE else LanguageKeys.PLAYER_OFFLINE,
                                ),
                            ).appendNewline()
                            .build(),
                    ).appendAndParseWithTranslatable(LanguageKeys.PLAYER_INFO_LOGS) {
                        it
                            .color(TextColor.fromHexString(BRANDING_PRIMARY_COLOUR))
                            .append(Component.text(" = ", TextColor.fromCSSHexString(COLOUR_WHITE)))
                            .append(
                                Component.translatable(
                                    if (result.hasLogs) LanguageKeys.PLAYER_AVAILABLE else LanguageKeys.PLAYER_UNAVAILABLE,
                                ),
                            ).clickEvent(ClickEvent.runCommand("/playerlogs ${result.player.name}"))
                            .appendNewline()
                    }

            return message.build()
        }

        data class PlayerInformationResult(
            val player: IdentityPlayer,
            val hasLogs: Boolean,
            val logsCount: String,
        )
    }
