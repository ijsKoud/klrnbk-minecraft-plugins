package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.ConnectionState
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.velocitypowered.api.proxy.Player

/** What the protocol layer needs to know about a connection. */
internal class ConnectionInfo(
    val clientVersion: ClientVersion,
    val state: ConnectionState,
)

/**
 * The only place that talks to PacketEvents' player/connection manager. A seam so the packet-building
 * logic can be tested with a recording fake, without a netty pipeline.
 */
internal interface PacketTransport {
    /** `null` when PacketEvents does not (or no longer) know the connection. */
    fun connection(player: Player): ConnectionInfo?

    /**
     * Sends [packet] to the client *without* running PacketEvents' listeners on it — the framework's own packets
     * must not be fed back into its own inbound/outbound hooks.
     */
    fun send(
        player: Player,
        packet: PacketWrapper<*>,
    )
}

internal class PacketEventsTransport : PacketTransport {
    override fun connection(player: Player): ConnectionInfo? {
        val user = PacketEvents.getAPI().playerManager.getUser(player) ?: return null
        return ConnectionInfo(user.clientVersion ?: return null, user.connectionState)
    }

    override fun send(
        player: Player,
        packet: PacketWrapper<*>,
    ) {
        val user = checkNotNull(PacketEvents.getAPI().playerManager.getUser(player)) { "PacketEvents does not know ${player.username}" }
        user.sendPacketSilently(packet)
    }
}
