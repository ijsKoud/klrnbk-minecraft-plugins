package nl.klrnbk.minecraft.plugins.whitelist.api.models

import java.time.Instant
import java.util.UUID

data class WhitelistedPlayer(
    /**
     * The Identity ID of the whitelisted player. NOTE: this is NOT the Mojang provided ID.
     */
    val playerId: UUID,
    /**
     * The Identity ID of whoever added the player, [nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi.CONSOLE_ACTOR_ID] for the console.
     */
    val actorId: UUID,
    /**
     * The date and time the player was added to the whitelist.
     */
    val whitelistedAt: Instant,
)
