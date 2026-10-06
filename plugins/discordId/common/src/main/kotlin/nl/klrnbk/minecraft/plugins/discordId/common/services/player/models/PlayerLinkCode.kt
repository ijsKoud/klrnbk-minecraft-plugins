package nl.klrnbk.minecraft.plugins.discordId.common.services.player.models

import kotlin.time.Instant

data class PlayerLinkCode(
    val code: String,
    val validUntil: Instant,
) {
    companion object {}
}
