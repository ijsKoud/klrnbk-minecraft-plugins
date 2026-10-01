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
import nl.klrnbk.minecraft.plugins.whitelist.common.services.logs.WhitelistLogsService
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil
import kotlin.time.toKotlinInstant
import kotlin.uuid.toKotlinUuid

/**
 * Builds the chat output of the whitelist log commands.
 */
@Singleton
class WhitelistLogsCommandFacade
    @Inject
    constructor(
        private val whitelistLogsService: WhitelistLogsService,
    ) {
        /**
         * The logs of a player being added to or removed from the whitelist.
         *
         * @param page The page to show, starting from 1.
         * @return null if Identity doesn't know a player with that name.
         */
        fun getPlayerLogs(
            playerName: String,
            page: Int,
        ): LogsPage? {
            val player = IdentityProvider.get().getPlayerFromName(playerName) ?: return null
            val playerId = player.id.toKotlinUuid()

            val entries =
                whitelistLogsService.getPlayerLogs(playerId, pagination(page)).map {
                    LogEntry(
                        timestamp = it.timestamp,
                        actorId = it.actorId,
                        key = if (it.isWhitelisted) LanguageKeys.LOGS_ENTRY_ADDED else LanguageKeys.LOGS_ENTRY_REMOVED,
                    )
                }

            return LogsPage(
                headerKey = LanguageKeys.LOGS_HEADER_PLAYER,
                headerArgument = player.name,
                entries = entries,
                page = page,
                totalPages = totalPages(whitelistLogsService.getPlayerLogsCount(playerId)),
                command = "/whitelist logs player ${player.name}",
            )
        }

        /**
         * The logs of the whitelist being toggled on or off.
         *
         * @param page The page to show, starting from 1.
         */
        fun getSettingsLogs(page: Int): LogsPage {
            val entries =
                whitelistLogsService.getSettingsLogs(pagination(page)).map {
                    LogEntry(
                        timestamp = it.timestamp,
                        actorId = it.actorId,
                        key = if (it.isWhitelistEnabled) LanguageKeys.LOGS_ENTRY_ENABLED else LanguageKeys.LOGS_ENTRY_DISABLED,
                    )
                }

            return LogsPage(
                headerKey = LanguageKeys.LOGS_HEADER_SETTINGS,
                headerArgument = null,
                entries = entries,
                page = page,
                totalPages = totalPages(whitelistLogsService.getSettingsLogsCount()),
                command = "/whitelist logs settings",
            )
        }

        fun produceMessage(result: LogsPage): TextComponent {
            val factory = MessageFactory.factory()
            val header = result.headerArgument?.let { arrayOf(Component.text(it)) } ?: emptyArray()

            factory
                .appendAndParseWithTranslatable(result.headerKey, *header) { it.decorate(TextDecoration.BOLD) }
                .appendNewlines(2)

            if (result.entries.isEmpty()) {
                factory.appendAndParseWithTranslatable(LanguageKeys.LOGS_EMPTY).appendNewlines(1)
            }

            factory.appendEntries(result.entries) {
                Component
                    .text()
                    .append(instantToComponentText(factory.miniMessage, it.timestamp.toKotlinInstant()))
                    .appendSpace()
                    .append(Component.translatable(it.key, actorName(it.actorId)))
                    .appendNewline()
                    .build()
            }

            val previous = (result.page - 1).coerceAtLeast(1)
            val next = (result.page + 1).coerceAtMost(result.totalPages)

            return factory
                .appendNewlines(1)
                .appendAndParseWithTranslatable(
                    LanguageKeys.LOGS_PAGE,
                    Component.text(result.page),
                    Component.text(result.totalPages),
                ).appendNewlines(1)
                .appendWithComponent(
                    Component.translatable(LanguageKeys.LOGS_FOOTER_PREVIOUS).clickEvent(ClickEvent.runCommand("${result.command} $previous")),
                ).appendText(" / ")
                .appendWithComponent(
                    Component.translatable(LanguageKeys.LOGS_FOOTER_NEXT).clickEvent(ClickEvent.runCommand("${result.command} $next")),
                ).build()
        }

        private fun actorName(actorId: UUID): Component {
            if (actorId == WhitelistApi.CONSOLE_ACTOR_ID) return Component.translatable(LanguageKeys.LOGS_ACTOR_CONSOLE)
            val name = IdentityProvider.get().getPlayerFromId(actorId)?.name
            // Never show the raw ID in chat.
            return if (name != null) Component.text(name) else Component.translatable(LanguageKeys.LOGS_ACTOR_UNKNOWN)
        }

        private fun pagination(page: Int) = QueryPagination(page = page - 1, itemsPerPage = ITEMS_PER_PAGE)

        private fun totalPages(count: Long) = ceil(count.toDouble() / ITEMS_PER_PAGE).toInt().coerceAtLeast(1)

        data class LogEntry(
            val timestamp: Instant,
            val actorId: UUID,
            val key: String,
        )

        data class LogsPage(
            val headerKey: String,
            val headerArgument: String?,
            val entries: List<LogEntry>,
            val page: Int,
            val totalPages: Int,
            val command: String,
        )

        companion object {
            const val ITEMS_PER_PAGE = 10
        }
    }
