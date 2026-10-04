package nl.klrnbk.minecraft.plugins.whitelist.common

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

/**
 * An in-memory [IdentityApi]. Adding players after registering is supported through [players].
 */
class FakeIdentityApi(
    val players: MutableList<IdentityPlayer> = mutableListOf(),
) : IdentityApi {
    override fun getPlayerFromUuid(uuid: UUID) = players.firstOrNull { it.playerId == uuid }

    override fun getPlayerFromId(id: UUID) = players.firstOrNull { it.id == id }

    override fun getPlayerFromName(name: String) = players.lastOrNull { it.name.equals(name, ignoreCase = true) }

    override fun getPlayersFromIds(ids: Collection<UUID>) = players.filter { it.id in ids }

    override fun getPlayerNames(
        prefix: String,
        limit: Int,
    ) = players
        .map { it.name }
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .sorted()
        .take(limit)

    override fun getAllPlayers(
        page: Int,
        itemsPerPage: Int,
    ) = players.sortedBy { it.name.lowercase() }.drop(page * itemsPerPage).take(itemsPerPage)

    override fun getPlayerCount() = players.size.toLong()
}

fun registerIdentity(vararg players: IdentityPlayer): FakeIdentityApi =
    FakeIdentityApi(players.toMutableList()).also { IdentityProvider.register(it) }
