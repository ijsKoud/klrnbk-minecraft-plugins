package nl.klrnbk.minecraft.plugins.discordId.common.services.player.transformers

import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.PlayerDiscordLinkCodeEntity
import nl.klrnbk.minecraft.plugins.discordId.common.services.player.models.PlayerLinkCode

fun PlayerLinkCode.Companion.fromEntity(entity: PlayerDiscordLinkCodeEntity): PlayerLinkCode =
    PlayerLinkCode(
        code = entity.code,
        validUntil = entity.validUntil,
        playerEntityId = entity.id.value,
    )
