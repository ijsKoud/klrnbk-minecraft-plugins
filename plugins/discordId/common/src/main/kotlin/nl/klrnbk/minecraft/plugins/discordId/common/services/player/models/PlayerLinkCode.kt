package nl.klrnbk.minecraft.plugins.discordId.common.services.player.models

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PlayerLinkCode(
    val code: String,
    val validUntil: Instant,
    val playerEntityId: Uuid,
) {
    companion object {}
}
