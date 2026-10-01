package nl.klrnbk.minecraft.plugins.identity.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.identity.common.LanguageKeys.PLAYER_LIST_HEADER
import nl.klrnbk.minecraft.plugins.identity.common.services.player.details.PlayerDetailsService
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import kotlin.math.ceil
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Singleton
class PlayerlistCommandFacade
    @Inject
    constructor(
        private val playerDetailsService: PlayerDetailsService,
    ) {
        fun getPlayerList(page: Int): PlayerListPage {
            val players =
                playerDetailsService
                    .getAllPlayerDetails(page, ITEMS_PER_PAGE)
                    .map { PlayerListPlayer(it.name, it.playerId.toKotlinUuid(), isOnline = it.isPlayerOnline) }

            val totalPlayers = playerDetailsService.getTotalPlayerCount()
            val totalPages = ceil((totalPlayers + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE.toDouble()).toInt()

            return PlayerListPage(
                players = players,
                currentPage = page.toString(),
                totalPages = totalPages.toString(),
                nextPage = if (page < totalPages) (page + 1).toString() else totalPages.toString(),
                previousPage = if (page > 1) (page - 1).toString() else "1",
            )
        }

        fun produceMessage(result: PlayerListPage): TextComponent {
            val message =
                MessageFactory
                    .factory()
                    .appendAndParseWithTranslatable(PLAYER_LIST_HEADER)
                    .appendNewlines(2)
                    .appendEntries(result.players) { player ->
                        Component
                            .translatable(
                                LanguageKeys.PLAYER_LIST_PLAYER_ENTRY,
                                Component.text(player.name),
                                Component.translatable(if (player.isOnline) LanguageKeys.PLAYER_ONLINE else LanguageKeys.PLAYER_OFFLINE),
                            ).hoverEvent(
                                HoverEvent.showText(
                                    Component.translatable(
                                        LanguageKeys.PLAYER_LIST_PLAYER_HOVER_TEXT,
                                        Component.text(player.name),
                                    ),
                                ),
                            ).clickEvent(ClickEvent.runCommand("/player ${player.name}"))
                            .appendNewline()
                    }.appendNewlines(1)
                    .appendAndParseWithTranslatable(
                        LanguageKeys.PLAYER_LIST_PAGE,
                        Component.text(result.currentPage),
                        Component.text(result.totalPages),
                    ).appendNewlines(1)
                    .appendWithComponent(
                        Component
                            .translatable(LanguageKeys.PLAYER_LIST_FOOTER_PREVIOUS)
                            .clickEvent(ClickEvent.runCommand("/playerlist ${result.previousPage}")),
                    ).appendText(" / ")
                    .appendWithComponent(
                        Component
                            .translatable(LanguageKeys.PLAYER_LIST_FOOTER_NEXT)
                            .clickEvent(ClickEvent.runCommand("/playerlist ${result.nextPage}")),
                    )

            return message.build()
        }

        companion object {
            const val ITEMS_PER_PAGE = 10
        }

        data class PlayerListPage(
            val players: List<PlayerListPlayer>,
            val currentPage: String,
            val totalPages: String,
            val nextPage: String,
            val previousPage: String,
        )

        data class PlayerListPlayer(
            val name: String,
            val uuid: Uuid,
            val isOnline: Boolean,
        )
    }
