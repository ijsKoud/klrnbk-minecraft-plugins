package nl.klrnbk.minecraft.plugins.discordId.common

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.Channel
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.managers.Presence
import net.dv8tion.jda.api.requests.restaction.CacheRestAction
import java.util.function.Consumer
import nl.klrnbk.minecraft.plugins.discordId.common.facades.ScheduledTasksFacade
import nl.klrnbk.minecraft.plugins.discordId.common.bot.BotMain
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.toKotlinUuid

/**
 * The Discord side, driven with mocked JDA events: the logic is tested, nothing talks to Discord.
 */
class BotTest {
    private val env = DiscordIdTestEnvironment(boosterRole = "booster-role").also { it.start() }
    private val alice = identityPlayer("Alice")
    private val identityId = alice.id.toKotlinUuid()

    init {
        registerIdentity(alice)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private fun role(id: String): Role = mockk { every { this@mockk.id } returns id }

    private fun discordUser(
        userId: String,
        userName: String = "user",
    ): User =
        mockk {
            every { id } returns userId
            every { name } returns userName
        }

    private class Slash(
        val event: SlashCommandInteractionEvent,
        val hook: InteractionHook,
    )

    private fun slash(
        commandName: String,
        user: User = discordUser("111", "alice#1"),
        options: Map<String, OptionMapping> = emptyMap(),
        roles: List<Role> = emptyList(),
    ): Slash {
        val hook = mockk<InteractionHook>(relaxed = true)
        val member = mockk<Member> { every { this@mockk.roles } returns roles }
        val event =
            mockk<SlashCommandInteractionEvent>(relaxed = true) {
                every { interaction.name } returns commandName
                every { this@mockk.user } returns user
                every { this@mockk.member } returns member
                every { this@mockk.hook } returns hook
                every { getOption(any()) } answers { options[firstArg()] }
            }
        return Slash(event, hook)
    }

    private fun stringOption(value: String): OptionMapping = mockk { every { asString } returns value }

    private fun userOption(user: User): OptionMapping = mockk { every { asUser } returns user }

    private fun requestCode(): String =
        env.codeService.getOrCreateCodeDetailsForPlayer(identityId).code

    // /link

    @Test
    fun `link defers privately and links the account with a valid code`() {
        val code = requestCode()
        val slash = slash("link", options = mapOf("code" to stringOption(code)))

        env.linkCommand.execute(slash.event)

        verify { slash.event.deferReply(true) }
        verify { slash.hook.editOriginal(LanguageKeys.LINK_CODE_SUCCESS) }
        assertEquals("111", env.linkService.getLinkDetailsByIdentityId(identityId)?.discordId)
        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    @Test
    fun `link records booster status from the members roles`() {
        val code = requestCode()
        val slash = slash("link", options = mapOf("code" to stringOption(code)), roles = listOf(role("other"), role("booster-role")))

        env.linkCommand.execute(slash.event)

        assertTrue(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    @Test
    fun `link answers an invalid code`() {
        val slash = slash("link", options = mapOf("code" to stringOption("wrong")))

        env.linkCommand.execute(slash.event)

        verify { slash.hook.editOriginal(LanguageKeys.LINK_CODE_INVALID) }
    }

    @Test
    fun `link answers with an error message instead of leaving the user waiting when something fails`() {
        val code = requestCode()
        env.databaseService.stop()
        val slash = slash("link", options = mapOf("code" to stringOption(code)))

        env.linkCommand.execute(slash.event)

        verify { slash.hook.editOriginal("An error occurred while linking your account, please try again later.") }
    }

    @Test
    fun `link does nothing without a code`() {
        val slash = slash("link")

        env.linkCommand.execute(slash.event)

        verify(exactly = 0) { slash.event.deferReply(any<Boolean>()) }
    }

    @Test
    fun `link is registered as a slash command with a required code option`() {
        val data = env.linkCommand.register() as SlashCommandData

        assertEquals("link", data.name)
        val option = data.options.single()
        assertEquals("code", option.name)
        assertEquals(OptionType.STRING, option.type)
        assertTrue(option.isRequired)
    }

    // /lookup

    @Test
    fun `lookup answers with the minecraft name of a linked user`() {
        env.linkService.linkDiscordWithPlayer(identityId, "222", "bob#1", false)
        val slash = slash("lookup", options = mapOf("user" to userOption(discordUser("222", "bob#1"))))

        env.lookupCommand.execute(slash.event)

        verify { slash.event.deferReply(true) }
        verify { slash.hook.editOriginal("Alice") }
    }

    @Test
    fun `lookup answers with an error message instead of leaving the user waiting when something fails`() {
        env.databaseService.stop()
        val slash = slash("lookup", options = mapOf("user" to userOption(discordUser("222", "bob#1"))))

        env.lookupCommand.execute(slash.event)

        verify { slash.hook.editOriginal("An error occurred while looking up the Minecraft name, please try again later.") }
    }

    @Test
    fun `lookup says when a user has not linked an account`() {
        val slash = slash("lookup", options = mapOf("user" to userOption(discordUser("333", "carol"))))

        env.lookupCommand.execute(slash.event)

        verify { slash.hook.editOriginal("carol hasn't connected their Minecraft account yet.") }
    }

    @Test
    fun `lookup is registered as a slash command with a required user option`() {
        val data = env.lookupCommand.register() as SlashCommandData

        assertEquals("lookup", data.name)
        assertEquals(OptionType.USER, data.options.single().type)
        assertTrue(data.options.single().isRequired)
    }

    // InteractionEvent

    @Test
    fun `slash commands are routed by name`() {
        val code = requestCode()
        val link = slash("link", options = mapOf("code" to stringOption(code)))
        val lookup = slash("lookup", options = mapOf("user" to userOption(discordUser("333", "carol"))))
        val other = slash("something-else")

        env.interactionEvent.onSlashCommandInteraction(link.event)
        env.interactionEvent.onSlashCommandInteraction(lookup.event)
        env.interactionEvent.onSlashCommandInteraction(other.event)

        verify { link.hook.editOriginal(LanguageKeys.LINK_CODE_SUCCESS) }
        verify { lookup.hook.editOriginal("carol hasn't connected their Minecraft account yet.") }
        verify(exactly = 0) { other.event.deferReply(any<Boolean>()) }
    }

    // BotMain

    @Test
    fun `stopping a bot that never started does not throw`() {
        // E.g. the plugin is disabled after the bot failed to log in because of a bad token.
        val bot =
            BotMain(
                env.configService,
                org.slf4j.helpers.NOPLogger.NOP_LOGGER,
                env.readyEvent,
                env.interactionEvent,
                env.userRenameEvent,
                env.roleChangeEvent,
                env.scheduledTasksFacade,
            )

        bot.stop()
    }

    // ReadyEvent

    @Test
    fun `when ready the commands are registered and the presence is set`() {
        val registered = mutableListOf<CommandData>()
        val presence = mockk<Presence>(relaxed = true)
        val jda =
            mockk<JDA>(relaxed = true) {
                every { upsertCommand(any<CommandData>()) } answers {
                    registered.add(firstArg())
                    mockk(relaxed = true)
                }
                every { this@mockk.presence } returns presence
            }
        val event = mockk<ReadyEvent>(relaxed = true) { every { this@mockk.jda } returns jda }
        val activity = slot<Activity>()

        env.readyEvent.onReady(event)

        assertEquals(setOf("link", "lookup"), registered.map { it.name }.toSet())
        verify { presence.setPresence(OnlineStatus.ONLINE, capture(activity)) }
        assertEquals(Activity.ActivityType.WATCHING, activity.captured.type)
        assertEquals("Discord & Minecraft players", activity.captured.name)
    }

    // UserRenameEvent

    @Test
    fun `renaming a discord user updates the linked player`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "old", false)
        val event = mockk<UserUpdateNameEvent> {
            every { newName } returns "new"
            every { user } returns discordUser("111")
        }

        env.userRenameEvent.onUserUpdateName(event)

        assertEquals("new", env.linkService.getLinkDetailsByIdentityId(identityId)?.discordName)
    }

    @Test
    fun `renaming a user who is not linked changes nothing`() {
        val event = mockk<UserUpdateNameEvent> {
            every { newName } returns "new"
            every { user } returns discordUser("999")
        }

        env.userRenameEvent.onUserUpdateName(event)

        assertTrue(env.linkService.getAllLinkedPlayers().isEmpty())
    }

    // RoleChangeEvent

    private fun roleAdded(
        userId: String,
        vararg added: Role,
    ) = mockk<GuildMemberRoleAddEvent> {
        every { roles } returns added.toList()
        every { user } returns discordUser(userId)
    }

    private fun roleRemoved(
        userId: String,
        vararg removed: Role,
    ) = mockk<GuildMemberRoleRemoveEvent> {
        every { roles } returns removed.toList()
        every { user } returns discordUser(userId)
    }

    @Test
    fun `getting the booster role marks the linked player as booster`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "alice#1", false)

        env.roleChangeEvent.onGuildMemberRoleAdd(roleAdded("111", role("booster-role")))

        assertTrue(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    @Test
    fun `role changes of users who are not linked are ignored`() {
        env.roleChangeEvent.onGuildMemberRoleAdd(roleAdded("999", role("booster-role")))

        assertTrue(env.linkService.getAllLinkedPlayers().isEmpty())
    }

    @Test
    fun `losing the booster role clears the booster status`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "alice#1", true)

        env.roleChangeEvent.onGuildMemberRoleRemove(roleRemoved("111", role("booster-role")))

        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    @Test
    fun `an unrelated role change does not touch the booster status`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "alice#1", true)

        env.roleChangeEvent.onGuildMemberRoleAdd(roleAdded("111", role("some-other-role")))

        assertTrue(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    // ScheduledTasksFacade

    @Test
    fun `the scheduled tasks can be stopped and started again, as a reload does`() {
        val jda = jdaWith(null, null)

        env.scheduledTasksFacade.start(jda)
        env.scheduledTasksFacade.stop()
        env.scheduledTasksFacade.start(jda)
        env.scheduledTasksFacade.stop()
    }

    @Test
    fun `a failing scheduled task is logged and does not break the next run`() {
        val logger = mockk<org.slf4j.Logger>(relaxed = true)
        val facade = ScheduledTasksFacade(env.configService, env.linkService, env.databaseService, logger)
        var runs = 0

        facade.runSafely("failing") {
            runs++
            throw IllegalStateException("database is down")
        }
        facade.runSafely("failing") { runs++ }

        assertEquals(2, runs)
        verify(exactly = 1) { logger.error(match<String> { it.contains("failing") }, any<Throwable>()) }
    }

    @Test
    fun `the cleanup task keeps running on a schedule even when the database fails`() {
        val facade = env.scheduledTasksFacade
        env.databaseService.stop() // every cleanup now throws

        // Exceptions from the task body are swallowed by runSafely, so this must not throw.
        facade.runSafely("link code cleanup") { env.databaseService.performCleanup() }
    }

    // The periodic check that repairs differences after downtime

    private fun jdaWith(
        user: User?,
        member: Member?,
    ): JDA {
        // retrieveMemberById(...).queue(success, failure) calls success with the member, or failure when Discord can't find them.
        val retrieve =
            mockk<CacheRestAction<Member>> {
                every { queue(any<Consumer<in Member>>(), any<Consumer<in Throwable>>()) } answers {
                    if (member != null) {
                        firstArg<Consumer<in Member>>().accept(member)
                    } else {
                        secondArg<Consumer<in Throwable>>().accept(IllegalStateException("unknown member"))
                    }
                }
            }
        val guild = mockk<Guild> { every { retrieveMemberById(any<String>()) } returns retrieve }
        val boosterRole =
            mockk<Role> {
                every { this@mockk.guild } returns guild
            }
        return mockk {
            every { getUserById(any<String>()) } returns user
            every { getRoleById("booster-role") } returns boosterRole
        }
    }

    @Test
    fun `the periodic check fixes a changed discord name and a changed booster status`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "old", false)
        val member = mockk<Member> { every { roles } returns listOf(role("booster-role")) }
        val jda = jdaWith(discordUser("111", "new"), member)

        env.scheduledTasksFacade.checkForPlayerLinkDifferences(jda)

        val details = checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId))
        assertEquals("new", details.discordName)
        assertTrue(details.isBooster)
    }

    @Test
    fun `the periodic check clears the booster status when the role is gone`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "same", true)
        val member = mockk<Member> { every { roles } returns emptyList() }
        val jda = jdaWith(discordUser("111", "same"), member)

        env.scheduledTasksFacade.checkForPlayerLinkDifferences(jda)

        assertFalse(checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId)).isBooster)
    }

    @Test
    fun `the periodic check logs members Discord can not find and leaves the player alone`() {
        env.linkService.linkDiscordWithPlayer(identityId, "111", "alice#1", true)
        env.ageLink(identityId, 1.days)
        val before = checkNotNull(env.linkService.getLinkDetailsByIdentityId(identityId))

        env.scheduledTasksFacade.checkForPlayerLinkDifferences(jdaWith(null, null))

        assertEquals(before, env.linkService.getLinkDetailsByIdentityId(identityId))
    }
}
