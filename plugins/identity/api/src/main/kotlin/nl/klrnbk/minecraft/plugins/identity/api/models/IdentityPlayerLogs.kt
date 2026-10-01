package nl.klrnbk.minecraft.plugins.identity.api.models

import nl.klrnbk.minecraft.packages.common.constants.ConnectionEventType
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class IdentityPlayerLogs(
    /**
     * The unique identifier generated for this log entry by the database.
     */
    val id: Uuid,
    /**
     * The unique identifier of this player. NOTE: this is the INTERNAL ID and not the Mojang provided ID
     */
    val playerId: Uuid,
    /**
     * The IP address of the player when they connected to the server. This may be null if the IP could not be determined or this setting is disabled.
     */
    val ip: String?,
    /**
     * The performed action by the player
     */
    val action: ConnectionEventType,
    /**
     * The server the player connected/disconnected to.
     */
    val server: IdentityPlayerLogsServer,
    /**
     * The date and time when the player connected/disconnected to the server.
     */
    val timestamp: Instant,
) {
    companion object
}

data class IdentityPlayerLogsServer(
    val name: String,
    val ip: String,
)
