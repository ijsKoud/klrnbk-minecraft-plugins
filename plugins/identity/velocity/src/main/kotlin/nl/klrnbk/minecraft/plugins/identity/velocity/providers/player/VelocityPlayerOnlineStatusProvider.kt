package nl.klrnbk.minecraft.plugins.identity.velocity.providers.player

import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.identity.common.providers.player.PlayerOnlineStatusProvider
import kotlin.jvm.optionals.getOrNull
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class VelocityPlayerOnlineStatusProvider(
    private val server: ProxyServer,
) : PlayerOnlineStatusProvider {
    override fun isPlayerOnline(playerId: Uuid): Boolean = server.getPlayer(playerId.toJavaUuid()).getOrNull()?.isActive != null
}
