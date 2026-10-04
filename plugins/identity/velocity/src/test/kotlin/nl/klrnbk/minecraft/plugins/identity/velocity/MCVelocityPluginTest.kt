package nl.klrnbk.minecraft.plugins.identity.velocity

import com.google.inject.Injector
import com.velocitypowered.api.proxy.InboundConnection
import com.velocitypowered.api.proxy.ProxyServer
import nl.klrnbk.minecraft.plugins.identity.api.IdentityApi
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.identity.api.models.IdentityPlayer
import nl.klrnbk.minecraft.plugins.identity.velocity.facades.PluginFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID

class MCVelocityPluginTest {
    private fun unsafeInstanceOf(clazz: Class<*>): Any {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as Unsafe
        return unsafe.allocateInstance(clazz)
    }

    private fun fakeInjector(
        pluginFacade: PluginFacade,
        api: IdentityApi,
    ): Injector =
        Proxy.newProxyInstance(
            Injector::class.java.classLoader,
            arrayOf(Injector::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getInstance" -> {
                    when (args[0]) {
                        PluginFacade::class.java -> pluginFacade
                        IdentityApi::class.java -> api
                        else -> null
                    }
                }

                else -> {
                    null
                }
            }
        } as Injector

    private fun fakeInboundConnection(): InboundConnection {
        val remoteAddress = InetSocketAddress("127.0.0.1", 25565)
        return Proxy.newProxyInstance(
            InboundConnection::class.java.classLoader,
            arrayOf(InboundConnection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getRemoteAddress" -> remoteAddress
                else -> null
            }
        } as InboundConnection
    }

    @Test
    fun `velocity plugin initializes without crashing`() {
        val server =
            Proxy.newProxyInstance(
                ProxyServer::class.java.classLoader,
                arrayOf(ProxyServer::class.java),
            ) { _, _, _ -> null } as ProxyServer
        val logger = org.slf4j.LoggerFactory.getLogger("velocity-test")
        val dataDirectory = Files.createTempDirectory("identity-velocity")

        val plugin = VelocityPlugin(logger, server, dataDirectory)

        assertNotNull(plugin)
    }

    @Test
    fun `identity provider can register and fetch values`() {
        val api =
            object : IdentityApi {
                override fun getPlayerFromUuid(uuid: UUID): IdentityPlayer? = null

                override fun getPlayerFromId(id: UUID): IdentityPlayer? = null

                override fun getPlayerFromName(name: String): IdentityPlayer? = null

                override fun getPlayersFromIds(ids: Collection<UUID>): List<IdentityPlayer> = emptyList()

                override fun getPlayerNames(prefix: String, limit: Int): List<String> = emptyList()

                override fun getAllPlayers(page: Int, itemsPerPage: Int): List<IdentityPlayer> = emptyList()

                override fun getPlayerCount(): Long = 0
            }

        IdentityProvider.unregister()
        IdentityProvider.register(api)

        assertEquals(api, IdentityProvider.get())
        IdentityProvider.unregister()
    }

    @Test
    fun `getPlayerIp builds a host and port string`() {
        val plugin = unsafeInstanceOf(VelocityPlugin::class.java) as VelocityPlugin
        val method = VelocityPlugin::class.java.getDeclaredMethod("getPlayerIp", InboundConnection::class.java)
        method.isAccessible = true

        val result = method.invoke(plugin, fakeInboundConnection()) as String

        assertEquals("127.0.0.1:25565", result)
    }
}
