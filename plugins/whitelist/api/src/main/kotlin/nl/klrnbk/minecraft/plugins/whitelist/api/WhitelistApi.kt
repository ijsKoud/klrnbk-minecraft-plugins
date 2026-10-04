package nl.klrnbk.minecraft.plugins.whitelist.api

import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistLog
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistSettingsLog
import nl.klrnbk.minecraft.plugins.whitelist.api.models.WhitelistedPlayer
import java.util.UUID

interface WhitelistApi {
    /**
     * Check if a player is whitelisted.
     *
     * @param identityId The UUID of the player received from Identity (NOT THE MOJANG UUID).
     * @return true if the player with the given UUID is whitelisted, false otherwise.
     */
    fun isPlayerWhitelisted(identityId: UUID): Boolean

    /**
     * Check if the whitelist is enabled.
     *
     * @return true if the whitelist is enabled, false otherwise.
     */
    fun isWhitelistEnabled(): Boolean

    /**
     * Enable or disable the whitelist. The change is logged.
     *
     * @param enabled Whether the whitelist should be enabled.
     * @param actorIdentityId The Identity ID of whoever performs the change, use [CONSOLE_ACTOR_ID] for the console.
     * @return true if the state changed, false if the whitelist already was in the requested state.
     */
    fun setWhitelistEnabled(
        enabled: Boolean,
        actorIdentityId: UUID,
    ): Boolean

    /**
     * Add a player to the whitelist. The change is logged.
     *
     * @param identityId The Identity ID of the player (NOT THE MOJANG UUID).
     * @param actorIdentityId The Identity ID of whoever performs the change, use [CONSOLE_ACTOR_ID] for the console.
     * @return true if the player was added, false if the player already was whitelisted.
     */
    fun addPlayerToWhitelist(
        identityId: UUID,
        actorIdentityId: UUID,
    ): Boolean

    /**
     * Remove a player from the whitelist. The change is logged.
     *
     * @param identityId The Identity ID of the player (NOT THE MOJANG UUID).
     * @param actorIdentityId The Identity ID of whoever performs the change, use [CONSOLE_ACTOR_ID] for the console.
     * @return true if the player was removed, false if the player was not whitelisted.
     */
    fun removePlayerFromWhitelist(
        identityId: UUID,
        actorIdentityId: UUID,
    ): Boolean

    /**
     * Get the players that are currently whitelisted, most recently added first.
     *
     * @param page The page to retrieve, starting from 0.
     * @param itemsPerPage The number of players per page.
     */
    fun getWhitelistedPlayers(
        page: Int = 0,
        itemsPerPage: Int = DEFAULT_ITEMS_PER_PAGE,
    ): List<WhitelistedPlayer>

    /**
     * Get the number of players that are currently whitelisted.
     */
    fun getWhitelistedPlayersCount(): Long

    /**
     * Get the whitelist add/remove logs of a player, newest first.
     *
     * @param identityId The Identity ID of the player (NOT THE MOJANG UUID).
     * @param page The page to retrieve, starting from 0.
     * @param itemsPerPage The number of logs per page.
     */
    fun getPlayerLogs(
        identityId: UUID,
        page: Int = 0,
        itemsPerPage: Int = DEFAULT_ITEMS_PER_PAGE,
    ): List<WhitelistLog>

    /**
     * Get the number of whitelist add/remove logs of a player.
     */
    fun getPlayerLogsCount(identityId: UUID): Long

    /**
     * Get the logs of the whitelist being toggled on or off, newest first.
     *
     * @param page The page to retrieve, starting from 0.
     * @param itemsPerPage The number of logs per page.
     */
    fun getSettingsLogs(
        page: Int = 0,
        itemsPerPage: Int = DEFAULT_ITEMS_PER_PAGE,
    ): List<WhitelistSettingsLog>

    /**
     * Get the number of logs of the whitelist being toggled on or off.
     */
    fun getSettingsLogsCount(): Long

    companion object {
        const val DEFAULT_ITEMS_PER_PAGE = 25

        /**
         * The actor ID that is used when an action is performed by the console.
         */
        val CONSOLE_ACTOR_ID: UUID = UUID(0L, 0L)
    }
}
