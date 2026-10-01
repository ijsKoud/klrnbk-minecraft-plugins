package nl.klrnbk.minecraft.plugins.whitelist.velocity

import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import java.time.Instant
import java.util.UUID

fun identityPlayer(name: String) =
    IdentityPlayer(
        id = UUID.randomUUID(),
        playerId = UUID.randomUUID(),
        name = name,
        firstJoined = Instant.now(),
        isPlayerOnline = false,
    )

fun registerIdentity(vararg players: IdentityPlayer) {
    IdentityProvider.register(
        object : IdentityApi {
            override fun getPlayerFromUuid(uuid: UUID) = players.firstOrNull { it.playerId == uuid }

            override fun getPlayerFromId(id: UUID) = players.firstOrNull { it.id == id }

            override fun getPlayerFromName(name: String) = players.firstOrNull { it.name.equals(name, ignoreCase = true) }
        },
    )
}
