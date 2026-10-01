package nl.klrnbk.minecraft.plugins.identity.paper.providers.player

import org.bukkit.Server
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class PaperPlayerOnlineStatusProvider(
    private val server: Server,
) : PlayerOnlineStatusProvider {
    override fun isPlayerOnline(playerId: Uuid): Boolean = server.onlinePlayers.any { it.uniqueId.toKotlinUuid() == playerId }
}
