package nl.klrnbk.minecraft.plugins.whitelist.api.models

import java.time.Instant
import java.util.UUID

data class WhitelistSettingsLog(
    /**
     * The unique identifier generated for this log entry by the database.
     */
    val id: UUID,
    /**
     * The Identity ID of whoever toggled the whitelist, [nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi.CONSOLE_ACTOR_ID] for the console.
     */
    val actorId: UUID,
    /**
     * Whether the whitelist was enabled (true) or disabled (false).
     */
    val isWhitelistEnabled: Boolean,
    /**
     * The date and time of the action.
     */
    val timestamp: Instant,
)
