package nl.klrnbk.minecraft.plugins.identity.api

import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import java.util.UUID

interface IdentityApi {
    /**
     * Get a player by their player UUID.
     *
     * @param uuid The UUID of the player.
     * @return The player with the given UUID, or null if no such player exists.
     */
    fun getPlayerFromUuid(uuid: UUID): IdentityPlayer?

    /**
     * Get a player by their Identity ID.
     * THIS IS DIFFERENT FROM THE PLAYER UUID.
     * The Identity ID is a unique identifier for the player in the Identity system, while the player UUID is the unique identifier for the player in Minecraft.
     *
     * @param id The ID of the player.
     * @return The player with the given ID, or null if no such player exists.
     */
    fun getPlayerFromId(id: UUID): IdentityPlayer?

    /**
     * Get a player by their name.
     *
     * @param name The name of the player, case-insensitive. If several players ever had this name, the one that joined last is returned.
     * @return The player with the given name, or null if no such player exists.
     */
    fun getPlayerFromName(name: String): IdentityPlayer?

    /**
     * Get multiple players by their Identity ID at once.
     * Prefer this over calling [getPlayerFromId] in a loop.
     *
     * @param ids The Identity IDs of the players (NOT the Minecraft UUIDs).
     * @return The players that exist, in no particular order. IDs that don't belong to a player are skipped.
     */
    fun getPlayersFromIds(ids: Collection<UUID>): List<IdentityPlayer>

    /**
     * Get the names of players, for example for command suggestions.
     *
     * @param prefix Only names starting with this prefix (case-insensitive) are returned. Empty returns all names.
     * @param limit The maximum number of names to return.
     * @return The matching names, sorted alphabetically.
     */
    fun getPlayerNames(
        prefix: String = "",
        limit: Int = DEFAULT_NAME_LIMIT,
    ): List<String>

    /**
     * Get every player known to Identity, sorted by name.
     *
     * @param page The page to retrieve, starting from 0.
     * @param itemsPerPage The number of players per page.
     */
    fun getAllPlayers(
        page: Int = 0,
        itemsPerPage: Int = DEFAULT_ITEMS_PER_PAGE,
    ): List<IdentityPlayer>

    /**
     * Get the number of players known to Identity.
     */
    fun getPlayerCount(): Long

    companion object {
        const val DEFAULT_NAME_LIMIT = 100
        const val DEFAULT_ITEMS_PER_PAGE = 25
    }
}
