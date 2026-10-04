package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.pkgs.i18n.factories.MessageFactory
import nl.klrnbk.minecraft.plugins.pkgs.i18n.utils.instantToComponentText
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil
import kotlin.time.toKotlinInstant

/**
 * Builds the chat output of `/whitelist list`.
 */
@Singleton
class WhitelistListCommandFacade
    @Inject
    constructor(
        private val playerWhitelistService: PlayerWhitelistService,
    ) {
        /**
         * @param page The page to show, starting from 1.
         */
        fun getPage(page: Int): ListPage {
            val whitelisted = playerWhitelistService.getWhitelistedPlayers(QueryPagination(page = page - 1, itemsPerPage = ITEMS_PER_PAGE))

            // One lookup for every name on the page, players and actors alike.
            val ids = (whitelisted.map { it.playerId } + whitelisted.map { it.actorId }).toSet()
            val names = IdentityProvider.get().getPlayersFromIds(ids).associate { it.id to it.name }

            return ListPage(
                entries =
                    whitelisted.map {
                        ListEntry(
                            playerName = names[it.playerId],
                            actor = it.actorId,
                            actorName = names[it.actorId],
                            whitelistedAt = it.whitelistedAt,
                        )
                    },
                page = page,
                totalPages = ceil(playerWhitelistService.getWhitelistedPlayersCount().toDouble() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1),
            )
        }

        fun produceMessage(result: ListPage): TextComponent {
            val factory = MessageFactory.factory()

            factory
                .appendAndParseWithTranslatable(LanguageKeys.LIST_HEADER) { it.decorate(TextDecoration.BOLD) }
                .appendNewlines(2)

            if (result.entries.isEmpty()) {
                factory.appendAndParseWithTranslatable(LanguageKeys.LIST_EMPTY).appendNewlines(1)
            }

            factory.appendEntries(result.entries) {
                val player =
                    it.playerName
                        ?.let { name -> Component.text(name).clickEvent(ClickEvent.runCommand("/whitelist logs player $name")) }
                        ?: Component.translatable(LanguageKeys.LOGS_ACTOR_UNKNOWN)

                Component
                    .text()
                    .append(
                        Component.translatable(
                            LanguageKeys.LIST_ENTRY,
                            player,
                            instantToComponentText(factory.miniMessage, it.whitelistedAt.toKotlinInstant()),
                            actor(it),
                        ),
                    ).appendNewline()
                    .build()
            }

            val previous = (result.page - 1).coerceAtLeast(1)
            val next = (result.page + 1).coerceAtMost(result.totalPages)

            return factory
                .appendNewlines(1)
                .appendAndParseWithTranslatable(LanguageKeys.LIST_PAGE, Component.text(result.page), Component.text(result.totalPages))
                .appendNewlines(1)
                .appendWithComponent(
                    Component.translatable(LanguageKeys.LIST_FOOTER_PREVIOUS).clickEvent(ClickEvent.runCommand("/whitelist list $previous")),
                ).appendText(" / ")
                .appendWithComponent(
                    Component.translatable(LanguageKeys.LIST_FOOTER_NEXT).clickEvent(ClickEvent.runCommand("/whitelist list $next")),
                ).build()
        }

        private fun actor(entry: ListEntry): Component =
            when {
                entry.actor == WhitelistApi.CONSOLE_ACTOR_ID -> Component.translatable(LanguageKeys.LOGS_ACTOR_CONSOLE)
                entry.actorName != null -> Component.text(entry.actorName)
                else -> Component.translatable(LanguageKeys.LOGS_ACTOR_UNKNOWN)
            }

        data class ListEntry(
            val playerName: String?,
            val actor: UUID,
            val actorName: String?,
            val whitelistedAt: Instant,
        )

        data class ListPage(
            val entries: List<ListEntry>,
            val page: Int,
            val totalPages: Int,
        )

        companion object {
            const val ITEMS_PER_PAGE = 10
        }
    }
