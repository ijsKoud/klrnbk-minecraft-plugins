package nl.klrnbk.minecraft.plugins.gui.velocity.protocol.packetevents

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.PacketEventsAPI
import com.github.retrooper.packetevents.manager.player.PlayerManager
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager
import com.github.retrooper.packetevents.manager.server.ServerManager
import com.github.retrooper.packetevents.manager.server.ServerVersion
import com.github.retrooper.packetevents.netty.NettyManager
import com.github.retrooper.packetevents.protocol.player.ClientVersion
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl
import io.mockk.every
import io.mockk.mockk

/**
 * A just-enough PacketEvents runtime for unit tests: a real netty buffer factory (so packets can be written and read back
 * byte for byte) and nothing else. No proxy, no channel injection.
 */
object PeTestSupport {
    private val netty = NettyManagerImpl()

    val api: PacketEventsAPI<Any> =
        object : PacketEventsAPI<Any>() {
            private var initialised = false
            private val players = mockk<PlayerManager>(relaxed = true)
            private val servers = mockk<ServerManager>(relaxed = true) { every { version } returns ServerVersion.V_26_2 }
            private val protocols = mockk<ProtocolManager>(relaxed = true)

            override fun isLoaded() = true

            override fun init() {
                initialised = true
            }

            override fun isInitialized() = initialised

            override fun isTerminated() = false

            override fun getPlugin(): Any = this

            override fun getServerManager(): ServerManager = servers

            override fun getProtocolManager(): ProtocolManager = protocols

            override fun getPlayerManager(): PlayerManager = players

            override fun getNettyManager(): NettyManager = netty

            override fun getInjector() = mockk<com.github.retrooper.packetevents.injector.ChannelInjector>(relaxed = true)
        }

    fun install() {
        if (PacketEvents.getAPI() !== api) PacketEvents.setAPI(api)
    }

    /** Serialises [packet] the way it would be written to a client running [client], and returns a reader positioned at the payload. */
    fun <T : PacketWrapper<T>> roundTrip(
        client: ClientVersion = ClientVersion.V_26_2,
        server: ServerVersion = ServerVersion.V_26_2,
        build: () -> PacketWrapper<T>,
    ): PacketWrapper<*> {
        install()
        val packet = build()
        packet.setClientVersion(client)
        packet.setServerVersion(server)
        packet.buffer = netty.byteBufAllocationOperator.buffer()
        packet.write()
        return PacketWrapper.createUniversalPacketWrapper(packet.buffer).also {
            it.setClientVersion(client)
            it.setServerVersion(server)
        }
    }
}
