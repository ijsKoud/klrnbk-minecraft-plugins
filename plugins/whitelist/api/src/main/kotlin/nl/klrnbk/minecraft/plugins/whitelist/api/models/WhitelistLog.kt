package nl.klrnbk.minecraft.plugins.whitelist.api.models

import java.time.Instant
import java.util.UUID

data class WhitelistLog(
    /**
     * The unique identifier generated for this log entry by the database.
     */
    val id: UUID,
    /**
     * The Identity ID of the player that was added to or removed from the whitelist. NOTE: this is NOT the Mojang provided ID.
     */
    val playerId: UUID,
    /**
     * The Identity ID of whoever performed the action, [nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi.CONSOLE_ACTOR_ID] for the console.
     */
    val actorId: UUID,
    /**
     * Whether the player was added (true) or removed (false).
     */
    val isWhitelisted: Boolean,
    /**
     * The date and time of the action.
     */
    val timestamp: Instant,
)
