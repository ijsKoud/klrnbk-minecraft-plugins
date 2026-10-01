package nl.klrnbk.minecraft.plugins.identity.api.models

import java.time.Instant
import java.util.UUID

data class IdentityPlayer(
    /**
     * The unique identifier generated for this player by the database. This is not the same as the player's Minecraft UUID, which is used to identify players across different servers and platforms.
     */
    val id: UUID,
    /**
     * The unique identifier for this player in the Minecraft game. This is used to identify players across different servers and platforms.
     */
    val playerId: UUID,
    /**
     * The name of the player.
     */
    val name: String,
    /**
     * The date and time when the player first joined the server.
     */
    val firstJoined: Instant,
    /**
     * Whether the player is currently online on the server.
     */
    val isPlayerOnline: Boolean,
) {
    companion object
}
