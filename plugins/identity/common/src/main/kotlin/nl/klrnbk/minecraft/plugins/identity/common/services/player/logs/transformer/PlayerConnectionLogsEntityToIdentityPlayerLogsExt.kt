package nl.klrnbk.minecraft.plugins.identity.common.services.player.logs.transformer

import nl.klrnbk.minecraft.packages.common.cryptography.CryptographyUtil
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogs
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayerLogsServer
import nl.klrnbk.minecraft.plugins.identity.common.providers.database.models.PlayerConnectionLogEntity
import javax.crypto.SecretKey

/**
 * Transforms a [PlayerConnectionLogEntity] to a [IdentityPlayerLogs].
 *
 * @param entity The entity to transform.
 * @param encryptionKey The encryption key to use for decrypting the player's IP address.
 * @return The transformed player details.
 */
fun IdentityPlayerLogs.Companion.fromEntity(
    entity: PlayerConnectionLogEntity,
    encryptionKey: SecretKey,
): IdentityPlayerLogs {
    val playerIp =
        if (entity.playerIp == null) {
            null
        } else {
            CryptographyUtil.decrypt(entity.playerIp!!, encryptionKey)
        }

    return IdentityPlayerLogs(
        id = entity.id.value,
        playerId = entity.playerId,
        ip = playerIp,
        action = entity.eventType,
        timestamp = entity.timestamp,
        server =
            IdentityPlayerLogsServer(
                ip = entity.serverIp,
                name = entity.serverName,
            ),
    )
}
