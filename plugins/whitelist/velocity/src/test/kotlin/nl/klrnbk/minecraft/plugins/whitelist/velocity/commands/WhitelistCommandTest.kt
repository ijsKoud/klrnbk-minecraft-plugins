package nl.klrnbk.minecraft.plugins.whitelist.velocity.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import io.mockk.every
import io.mockk.mockk
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import nl.klrnbk.minecraft.plugins.whitelist.api.WhitelistApi
import nl.klrnbk.minecraft.plugins.whitelist.common.LanguageKeys
import nl.klrnbk.minecraft.plugins.whitelist.common.Permissions
import nl.klrnbk.minecraft.plugins.whitelist.common.WhitelistTestEnvironment
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.AdminCommandsFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistApiFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistCommandFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistListCommandFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.facades.WhitelistLogsCommandFacade
import nl.klrnbk.minecraft.plugins.whitelist.common.identityPlayer
import nl.klrnbk.minecraft.plugins.whitelist.common.registerIdentity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger

class WhitelistCommandTest {
    private val env = WhitelistTestEnvironment().also { it.start() }
    private val alice = identityPlayer("Alice")
    private val admin = identityPlayer("Admin")

    private val api: WhitelistApi = WhitelistApiFacade(env.playerWhitelistService, env.activeStatusService, env.logsService)
    private val dispatcher = CommandDispatcher<CommandSource>()

    init {
        registerIdentity(alice, admin)

        val command =
            WhitelistCommand(
                WhitelistCommandFacade(env.playerWhitelistService, env.activeStatusService),
                AdminCommandsFacade(NOPLogger.NOP_LOGGER, env.configService, env.activeStatusService, env.databaseService),
                WhitelistLogsCommandFacade(env.logsService),
                WhitelistListCommandFacade(env.playerWhitelistService),
                NOPLogger.NOP_LOGGER,
                env.dataDirectory,
            )
        dispatcher.root.addChild(command.configure().node)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private val sent = mutableListOf<Component>()

    private fun console(vararg permissions: String): CommandSource =
        mockk {
            every { hasPermission(any()) } answers { firstArg<String>() in permissions }
            every { sendMessage(any<Component>()) } answers { sent.add(firstArg()) }
        }

    private fun playerSource(vararg permissions: String): CommandSource =
        mockk<Player> {
            every { uniqueId } returns admin.playerId
            every { hasPermission(any()) } answers { firstArg<String>() in permissions }
            every { sendMessage(any<Component>()) } answers { sent.add(firstArg()) }
        }

    private fun lastMessageKey(): String =
        sent
            .last()
            .children()
            .filterIsInstance<TranslatableComponent>()
            .single()
            .key()

    private val allPermissions =
        arrayOf(
            Permissions.TOGGLE_WHITELIST,
            Permissions.ADD_PLAYER,
            Permissions.REMOVE_PLAYER,
            Permissions.VIEW_LOGS,
            Permissions.VIEW_LIST,
            Permissions.RELOAD_PLUGIN,
        )

    // Permissions

    @Test
    fun `nothing is allowed without permissions`() {
        val source = console()

        listOf(
            "whitelist",
            "whitelist on",
            "whitelist off",
            "whitelist add Alice",
            "whitelist remove Alice",
            "whitelist reload",
            "whitelist logs settings",
            "whitelist logs player Alice",
            "whitelist list",
        ).forEach {
            assertThrows(CommandSyntaxException::class.java, { dispatcher.execute(it, source) }, it)
        }

        assertFalse(api.isWhitelistEnabled())
        assertFalse(api.isPlayerWhitelisted(alice.id))
    }

    @Test
    fun `each subcommand needs its own permission`() {
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("whitelist add Alice", console(Permissions.REMOVE_PLAYER)) }
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("whitelist remove Alice", console(Permissions.ADD_PLAYER)) }
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("whitelist on", console(Permissions.ADD_PLAYER)) }
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("whitelist reload", console(Permissions.TOGGLE_WHITELIST)) }
    }

    // Toggle

    @Test
    fun `on and off toggle the whitelist and log who did it`() {
        val source = playerSource(Permissions.TOGGLE_WHITELIST)

        assertEquals(0, dispatcher.execute("whitelist on", source))
        assertTrue(api.isWhitelistEnabled())
        assertEquals(LanguageKeys.WHITELIST_ENABLED, lastMessageKey())

        assertEquals(0, dispatcher.execute("whitelist off", source))
        assertFalse(api.isWhitelistEnabled())
        assertEquals(LanguageKeys.WHITELIST_DISABLED, lastMessageKey())

        val logs = api.getSettingsLogs()
        assertEquals(listOf(false, true), logs.map { it.isWhitelistEnabled })
        assertTrue(logs.all { it.actorId == admin.id })
    }

    @Test
    fun `toggling to the current state is reported`() {
        val source = console(Permissions.TOGGLE_WHITELIST)

        dispatcher.execute("whitelist off", source)
        assertEquals(LanguageKeys.WHITELIST_ALREADY_DISABLED, lastMessageKey())

        dispatcher.execute("whitelist on", source)
        dispatcher.execute("whitelist on", source)
        assertEquals(LanguageKeys.WHITELIST_ALREADY_ENABLED, lastMessageKey())
        assertEquals(1, api.getSettingsLogsCount())
    }

    @Test
    fun `the console is logged as the console actor`() {
        dispatcher.execute("whitelist on", console(Permissions.TOGGLE_WHITELIST))

        assertEquals(WhitelistApi.CONSOLE_ACTOR_ID, api.getSettingsLogs().single().actorId)
    }

    // Add / remove

    @Test
    fun `add whitelists a player and logs who did it`() {
        assertEquals(0, dispatcher.execute("whitelist add Alice", playerSource(Permissions.ADD_PLAYER)))

        assertTrue(api.isPlayerWhitelisted(alice.id))
        assertEquals(LanguageKeys.PLAYER_ADDED, lastMessageKey())
        assertEquals(admin.id, api.getPlayerLogs(alice.id).single().actorId)
    }

    @Test
    fun `adding a whitelisted player again is reported`() {
        val source = console(Permissions.ADD_PLAYER)
        dispatcher.execute("whitelist add Alice", source)

        assertEquals(1, dispatcher.execute("whitelist add Alice", source))

        assertEquals(LanguageKeys.PLAYER_ALREADY_WHITELISTED, lastMessageKey())
        assertEquals(1, api.getPlayerLogsCount(alice.id))
    }

    @Test
    fun `remove un-whitelists a player and logs who did it`() {
        api.addPlayerToWhitelist(alice.id, admin.id)

        assertEquals(0, dispatcher.execute("whitelist remove Alice", playerSource(Permissions.REMOVE_PLAYER)))

        assertFalse(api.isPlayerWhitelisted(alice.id))
        assertEquals(LanguageKeys.PLAYER_REMOVED, lastMessageKey())
        assertFalse(api.getPlayerLogs(alice.id).first().isWhitelisted)
    }

    @Test
    fun `removing a player that is not whitelisted is reported`() {
        assertEquals(1, dispatcher.execute("whitelist remove Alice", console(Permissions.REMOVE_PLAYER)))

        assertEquals(LanguageKeys.PLAYER_NOT_WHITELISTED, lastMessageKey())
    }

    @Test
    fun `unknown players are reported`() {
        assertEquals(1, dispatcher.execute("whitelist add Nobody", console(Permissions.ADD_PLAYER)))

        assertEquals(LanguageKeys.PLAYER_NOT_FOUND, lastMessageKey())
    }

    @Test
    fun `a failing database is reported instead of thrown`() {
        env.databaseService.stop()

        assertEquals(1, dispatcher.execute("whitelist add Alice", console(Permissions.ADD_PLAYER)))

        assertEquals(LanguageKeys.ACTION_FAILED, lastMessageKey())
    }

    // Reload

    @Test
    fun `reload reloads the plugin`() {
        env.dataDirectory.resolve("whitelist_enabled.txt").toFile().writeText("true")
        assertFalse(api.isWhitelistEnabled())

        assertEquals(0, dispatcher.execute("whitelist reload", console(Permissions.RELOAD_PLUGIN)))

        assertEquals(LanguageKeys.RELOAD_SUCCESS, lastMessageKey())
        assertTrue(api.isWhitelistEnabled())
    }

    // List

    private fun suggestions(input: String, source: CommandSource) =
        dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get().list.map { it.text }

    @Test
    fun `list needs its own permission`() {
        assertThrows(CommandSyntaxException::class.java) { dispatcher.execute("whitelist list", console(Permissions.VIEW_LOGS)) }
    }

    @Test
    fun `list shows the whitelisted players by name with who added them`() {
        api.addPlayerToWhitelist(alice.id, admin.id)

        assertEquals(0, dispatcher.execute("whitelist list", console(Permissions.VIEW_LIST)))

        val keys = translatableKeys(sent.last())
        assertEquals(LanguageKeys.LIST_HEADER, keys.first())
        assertEquals(1, keys.count { it == LanguageKeys.LIST_ENTRY })
        val text = plainText(sent.last())
        assertTrue(text.contains(alice.name))
        assertTrue(text.contains(admin.name))
        assertFalse(text.contains(alice.id.toString()))
    }

    @Test
    fun `list leaves out removed players and shows the console as the console`() {
        api.addPlayerToWhitelist(alice.id, WhitelistApi.CONSOLE_ACTOR_ID)
        api.addPlayerToWhitelist(admin.id, WhitelistApi.CONSOLE_ACTOR_ID)
        api.removePlayerFromWhitelist(admin.id, WhitelistApi.CONSOLE_ACTOR_ID)

        dispatcher.execute("whitelist list", console(Permissions.VIEW_LIST))

        assertEquals(1, translatableKeys(sent.last()).count { it == LanguageKeys.LIST_ENTRY })
        assertTrue(translatableKeys(sent.last()).contains(LanguageKeys.LOGS_ACTOR_CONSOLE))
        assertFalse(plainText(sent.last()).contains(admin.name))
    }

    @Test
    fun `list is paginated and reports an empty whitelist`() {
        val source = console(Permissions.VIEW_LIST)
        dispatcher.execute("whitelist list", source)
        assertTrue(translatableKeys(sent.last()).contains(LanguageKeys.LIST_EMPTY))

        val fake = IdentityProvider.get() as nl.klrnbk.minecraft.plugins.whitelist.common.FakeIdentityApi
        repeat(12) {
            val player = identityPlayer("Player$it")
            fake.players.add(player)
            api.addPlayerToWhitelist(player.id, admin.id)
        }

        dispatcher.execute("whitelist list", source)
        assertEquals(10, translatableKeys(sent.last()).count { it == LanguageKeys.LIST_ENTRY })
        dispatcher.execute("whitelist list 2", source)
        assertEquals(2, translatableKeys(sent.last()).count { it == LanguageKeys.LIST_ENTRY })
    }

    // Suggestions

    @Test
    fun `add suggests players from identity even when they are offline`() {
        assertEquals(listOf("Admin", "Alice"), suggestions("whitelist add ", console(Permissions.ADD_PLAYER)).sorted())
        assertEquals(listOf("Alice"), suggestions("whitelist add al", console(Permissions.ADD_PLAYER)))
    }

    @Test
    fun `remove only suggests whitelisted players`() {
        api.addPlayerToWhitelist(alice.id, admin.id)

        assertEquals(listOf("Alice"), suggestions("whitelist remove ", console(Permissions.REMOVE_PLAYER)))
    }

    @Test
    fun `log commands suggest players from identity`() {
        assertEquals(listOf("Admin", "Alice"), suggestions("whitelist logs player ", console(Permissions.VIEW_LOGS)).sorted())
    }

    // Logs

    // Translatable arguments (the actor and player names) aren't children, so they are walked explicitly.
    private fun flattened(component: Component): List<Component> =
        listOf(component) +
            component.children().flatMap { flattened(it) } +
            ((component as? TranslatableComponent)?.arguments().orEmpty()).flatMap { flattened(it.asComponent()) }

    private fun plainText(component: Component): String =
        flattened(component).filterIsInstance<net.kyori.adventure.text.TextComponent>().joinToString(" ") { it.content() }

    private fun translatableKeys(component: Component) = flattened(component).filterIsInstance<TranslatableComponent>().map { it.key() }

    @Test
    fun `logs need the logs permission`() {
        assertThrows(CommandSyntaxException::class.java) {
            dispatcher.execute("whitelist logs settings", console(Permissions.TOGGLE_WHITELIST))
        }
    }

    @Test
    fun `settings logs are shown in chat`() {
        api.setWhitelistEnabled(true, admin.id)
        Thread.sleep(5)
        api.setWhitelistEnabled(false, WhitelistApi.CONSOLE_ACTOR_ID)

        assertEquals(0, dispatcher.execute("whitelist logs settings", console(Permissions.VIEW_LOGS)))

        val keys = translatableKeys(sent.last())
        assertEquals(
            listOf(
                LanguageKeys.LOGS_HEADER_SETTINGS,
                LanguageKeys.LOGS_ENTRY_DISABLED,
                LanguageKeys.LOGS_ACTOR_CONSOLE,
                LanguageKeys.LOGS_ENTRY_ENABLED,
                LanguageKeys.LOGS_PAGE,
                LanguageKeys.LOGS_FOOTER_PREVIOUS,
                LanguageKeys.LOGS_FOOTER_NEXT,
            ),
            keys,
        )
        // The player actor is rendered by name.
        assertTrue(plainText(sent.last()).contains(admin.name))
    }

    @Test
    fun `player logs are shown in chat with the actor name`() {
        api.addPlayerToWhitelist(alice.id, admin.id)
        Thread.sleep(5)
        api.removePlayerFromWhitelist(alice.id, admin.id)

        assertEquals(0, dispatcher.execute("whitelist logs player alice", console(Permissions.VIEW_LOGS)))

        val keys = translatableKeys(sent.last())
        assertEquals(LanguageKeys.LOGS_HEADER_PLAYER, keys.first())
        assertEquals(listOf(LanguageKeys.LOGS_ENTRY_REMOVED, LanguageKeys.LOGS_ENTRY_ADDED), keys.filter { it.startsWith("whitelist.logs.entry") })
        val text = plainText(sent.last())
        assertTrue(text.contains(alice.name))
        assertTrue(text.contains(admin.name))
    }

    @Test
    fun `an actor unknown to identity is never shown as an id`() {
        val stranger = java.util.UUID.randomUUID()
        api.setWhitelistEnabled(true, stranger)

        dispatcher.execute("whitelist logs settings", console(Permissions.VIEW_LOGS))

        assertTrue(translatableKeys(sent.last()).contains(LanguageKeys.LOGS_ACTOR_UNKNOWN))
        assertFalse(plainText(sent.last()).contains(stranger.toString()))
    }

    @Test
    fun `logs are paginated`() {
        repeat(12) { api.setWhitelistEnabled(it % 2 == 0, admin.id) }
        val source = console(Permissions.VIEW_LOGS)

        dispatcher.execute("whitelist logs settings", source)
        assertEquals(10, translatableKeys(sent.last()).count { it.startsWith("whitelist.logs.entry") })

        dispatcher.execute("whitelist logs settings 2", source)
        assertEquals(2, translatableKeys(sent.last()).count { it.startsWith("whitelist.logs.entry") })
    }

    @Test
    fun `empty logs and unknown players are reported`() {
        val source = console(Permissions.VIEW_LOGS)

        dispatcher.execute("whitelist logs settings", source)
        assertTrue(translatableKeys(sent.last()).contains(LanguageKeys.LOGS_EMPTY))

        assertEquals(1, dispatcher.execute("whitelist logs player Nobody", source))
        assertEquals(LanguageKeys.PLAYER_NOT_FOUND, lastMessageKey())
    }

    @Test
    fun `all permissions together give access to everything`() {
        val source = console(*allPermissions)

        assertEquals(0, dispatcher.execute("whitelist on", source))
        assertEquals(0, dispatcher.execute("whitelist add Alice", source))
        assertEquals(0, dispatcher.execute("whitelist remove Alice", source))
        assertEquals(0, dispatcher.execute("whitelist logs settings", source))
        assertEquals(0, dispatcher.execute("whitelist list", source))
        assertEquals(0, dispatcher.execute("whitelist reload", source))
    }
}
