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
     * @param name The name of the player.
     * @return The player with the given name, or null if no such player exists.
     */
    fun getPlayerFromName(name: String): IdentityPlayer?
}
