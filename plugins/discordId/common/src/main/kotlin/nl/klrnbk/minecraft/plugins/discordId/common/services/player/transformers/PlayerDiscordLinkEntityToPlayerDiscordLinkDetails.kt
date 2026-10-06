package nl.klrnbk.minecraft.plugins.discordId.common.services.player.transformers

import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkEntity
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.models.PlayerDiscordLinkDetails

fun PlayerDiscordLinkDetails.Companion.fromEntity(entity: PlayerDiscordLinkEntity): PlayerDiscordLinkDetails =
    PlayerDiscordLinkDetails(
        identityId = entity.id.value,
        discordId = entity.discordId,
        discordName = entity.discordName,
        isBooster = entity.isBooster,
        isLinked = entity.discordId?.isNotEmpty() ?: false,
        lastUpdatedAt = entity.lastUpdatedAt,
    )
