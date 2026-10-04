package nl.klrnbk.minecraft.plugins.whitelist.common.facades

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.common.services.status.ActiveStatusService
import nl.klrnbk.minecraft.plugins.whitelist.common.services.whitelist.PlayerWhitelistService
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

enum class WhitelistActionResult {
    /** The action was performed. */
    SUCCESS,

    /** Nothing changed, the target already was in the requested state. */
    NO_CHANGE,

    /** No player with the given name is known to Identity. */
    PLAYER_NOT_FOUND,

    /** The command sender is not known to Identity, so the action can't be attributed to them. */
    ACTOR_NOT_FOUND,
}

@Singleton
class WhitelistCommandFacade
    @Inject
    constructor(
        private val playerWhitelistService: PlayerWhitelistService,
        private val activeStatusService: ActiveStatusService,
    ) {
        fun addPlayer(
            playerName: String,
            actorPlayerId: UUID?,
        ): WhitelistActionResult =
            perform(playerName, actorPlayerId) { player, actor ->
                playerWhitelistService.addPlayerToWhitelist(player.toKotlinUuid(), actor.toKotlinUuid())
            }

        fun removePlayer(
            playerName: String,
            actorPlayerId: UUID?,
        ): WhitelistActionResult =
            perform(playerName, actorPlayerId) { player, actor ->
                playerWhitelistService.removePlayerFromWhitelist(player.toKotlinUuid(), actor.toKotlinUuid())
            }

        fun setWhitelistEnabled(
            enabled: Boolean,
            actorPlayerId: UUID?,
        ): WhitelistActionResult {
            val actor = resolveActorIdentityId(actorPlayerId) ?: return WhitelistActionResult.ACTOR_NOT_FOUND
            return activeStatusService.setWhitelistEnabled(enabled, actor.toKotlinUuid()).toResult()
        }

        /**
         * Names of the players known to Identity that start with [prefix], for `/whitelist add` and the log commands.
         */
        fun suggestPlayerNames(prefix: String): List<String> = IdentityProvider.get().getPlayerNames(prefix, MAX_SUGGESTIONS)

        /**
         * Names of the whitelisted players that start with [prefix], for `/whitelist remove`.
         */
        fun suggestWhitelistedPlayerNames(prefix: String): List<String> {
            val whitelisted = playerWhitelistService.getWhitelistedPlayers(QueryPagination(page = 0, itemsPerPage = WHITELIST_SUGGESTION_SCAN))

            return IdentityProvider
                .get()
                .getPlayersFromIds(whitelisted.map { it.playerId })
                .map { it.name }
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .sorted()
                .take(MAX_SUGGESTIONS)
        }

        /**
         * Identity ID of a player by name, null if Identity doesn't know the player.
         */
        fun findPlayerIdentityId(playerName: String): UUID? = IdentityProvider.get().getPlayerFromName(playerName)?.id

        /**
         * @param actorPlayerId The Minecraft UUID of the command sender, null for the console.
         * @return the Identity ID to attribute the action to, null if the sender is unknown to Identity.
         */
        private fun resolveActorIdentityId(actorPlayerId: UUID?): UUID? {
            if (actorPlayerId == null) return WhitelistApi.CONSOLE_ACTOR_ID
            return IdentityProvider.get().getPlayerFromUuid(actorPlayerId)?.id
        }

        private fun perform(
            playerName: String,
            actorPlayerId: UUID?,
            action: (player: UUID, actor: UUID) -> Boolean,
        ): WhitelistActionResult {
            val actor = resolveActorIdentityId(actorPlayerId) ?: return WhitelistActionResult.ACTOR_NOT_FOUND
            val player = findPlayerIdentityId(playerName) ?: return WhitelistActionResult.PLAYER_NOT_FOUND

            return action(player, actor).toResult()
        }

        private companion object {
            const val MAX_SUGGESTIONS = 100
            const val WHITELIST_SUGGESTION_SCAN = 1000
        }

        private fun Boolean.toResult() = if (this) WhitelistActionResult.SUCCESS else WhitelistActionResult.NO_CHANGE
    }
