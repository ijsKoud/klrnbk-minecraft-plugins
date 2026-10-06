package nl.klrnbk.minecraft.plugins.discordId.common.services.player.models

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PlayerDiscordLinkDetails(
    val identityId: Uuid,
    val discordId: String?,
    val discordName: String?,
    val isLinked: Boolean,
    val isBooster: Boolean,
    val lastUpdatedAt: Instant,
) {
    companion object {}
}
