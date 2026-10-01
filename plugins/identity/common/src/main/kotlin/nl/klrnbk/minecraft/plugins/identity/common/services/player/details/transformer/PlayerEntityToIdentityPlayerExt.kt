package nl.klrnbk.minecraft.plugins.identity.common.services.player.details.transformer

import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerEntity
import kotlin.time.toJavaInstant
import kotlin.uuid.toJavaUuid

/**
 * Transforms a [PlayerEntity] to a [IdentityPlayer].
 *
 * @param entity The player entity to transform.
 * @param isPlayerOnline Whether the player is online.
 * @return The transformed player details.
 */
fun IdentityPlayer.Companion.fromEntity(
    entity: PlayerEntity,
    isPlayerOnline: Boolean,
    updatedName: String? = null,
): IdentityPlayer =
    IdentityPlayer(
        id = entity.id.value.toJavaUuid(),
        playerId = entity.playerId.toJavaUuid(),
        name = updatedName ?: entity.name,
        firstJoined = entity.firstJoined.toJavaInstant(),
        isPlayerOnline = isPlayerOnline,
    )
