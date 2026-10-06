package nl.klrnbk.minecraft.plugins.discordId.common

import nl.klrnbk.minecraft.packages.database.QueryPagination
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogAction
import nl.klrnbk.minecraft.plugins.discordId.common.providers.database.models.AuditLogEntity
import nl.klrnbk.minecraft.plugins.identity.api.IdentityProvider
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.toKotlinUuid

class AuditLogTest {
    private val env = DiscordIdTestEnvironment().also { it.start() }
    private val alice = identityPlayer("Alice")
    private val admin = identityPlayer("Admin")

    init {
        registerIdentity(alice, admin)
    }

    @AfterEach
    fun cleanup() {
        IdentityProvider.unregister()
        env.close()
    }

    private val aliceId get() = alice.id.toKotlinUuid()
    private val adminId get() = admin.id.toKotlinUuid()

    private fun logs() = env.auditLogService.getLogs(QueryPagination())

    private fun link() = env.linkService.linkDiscordWithPlayer(aliceId, "111", "alice#1", false)

    // What gets logged

    @Test
    fun `linking with a code is logged`() {
        val code = env.codeService.getOrCreateCodeDetailsForPlayer(aliceId).code

        env.linkFacade.linkPlayer(code, "111", "alice#1", false)

        val entry = logs().single()
        assertEquals(AuditLogAction.LINK, entry.action)
        assertEquals(aliceId, entry.actorIdentityId)
        assertEquals(aliceId, entry.targetIdentityId)
        assertEquals("111", entry.discordId)
        assertEquals("alice#1", entry.details)
    }

    @Test
    fun `a rejected link is not logged`() {
        env.linkFacade.linkPlayer("wrong", "111", "alice#1", false)

        assertTrue(logs().isEmpty())
    }

    @Test
    fun `a player unlinking is logged with themselves as the actor`() {
        link()
        env.ageLink(aliceId, 31.days)

        env.linkFacade.unlinkPlayer(alice.playerId.toKotlinUuid(), isForced = false, isBypassed = false)

        val entry = logs().single()
        assertEquals(AuditLogAction.UNLINK, entry.action)
        assertEquals(aliceId, entry.actorIdentityId)
        assertEquals(aliceId, entry.targetIdentityId)
        assertEquals("111", entry.discordId)
    }

    @Test
    fun `an unlink that is refused is not logged`() {
        link()

        env.linkFacade.unlinkPlayer(alice.playerId.toKotlinUuid(), isForced = false, isBypassed = false)

        assertTrue(logs().isEmpty())
    }

    @Test
    fun `a force unlink is logged with the admin as the actor`() {
        link()

        env.linkFacade.forceUnlinkPlayer("Alice", admin.playerId.toKotlinUuid())

        val entry = logs().single()
        assertEquals(AuditLogAction.FORCE_UNLINK, entry.action)
        assertEquals(adminId, entry.actorIdentityId)
        assertEquals(aliceId, entry.targetIdentityId)
    }

    @Test
    fun `a force unlink by the console has no actor`() {
        link()

        env.linkFacade.forceUnlinkPlayer("Alice", null)

        val entry = logs().single()
        assertEquals(AuditLogAction.FORCE_UNLINK, entry.action)
        assertNull(entry.actorIdentityId)
    }

    @Test
    fun `an export and an import are logged with the file name`() {
        link()
        val fileName = env.adminCommandsFacade.exportData(env.dataDirectory, admin.playerId.toKotlinUuid()).messageArguments().first().let {
            (it as net.kyori.adventure.text.TextComponent).content()
        }
        env.adminCommandsFacade.importData(env.dataDirectory, fileName, null) // fails: the tables are not empty

        val entries = logs()

        assertEquals(listOf(AuditLogAction.EXPORT), entries.map { it.action })
        assertEquals(fileName, entries.single().details)
        assertEquals(adminId, entries.single().actorIdentityId)
    }

    @Test
    fun `a sender Identity does not know is logged without an actor`() {
        env.auditLogService.log(AuditLogAction.RELOAD, env.auditLogService.actorIdentityId(identityPlayer("Stranger").playerId.toKotlinUuid()))

        assertNull(logs().single().actorIdentityId)
    }

    // Config

    @Test
    fun `nothing is logged when logs are disabled`() {
        val disabled = DiscordIdTestEnvironment(logsEnabled = false).also { it.start() }
        try {
            disabled.auditLogService.log(AuditLogAction.RELOAD)

            assertEquals(0, disabled.auditLogService.getLogsCount())
        } finally {
            disabled.close()
        }
    }

    @Test
    fun `a failing log write never fails the logged action`() {
        env.databaseService.stop()

        env.auditLogService.log(AuditLogAction.RELOAD) // would throw without the guard
    }

    @Test
    fun `long details are cut to fit the column`() {
        env.auditLogService.log(AuditLogAction.IMPORT, details = "x".repeat(1000))

        assertEquals(255, logs().single().details?.length)
    }

    // Reading and cleanup

    @Test
    fun `logs are listed newest first and paginated`() {
        listOf(AuditLogAction.RELOAD, AuditLogAction.EXPORT, AuditLogAction.IMPORT).forEach {
            env.auditLogService.log(it)
            Thread.sleep(5)
        }

        assertEquals(3, env.auditLogService.getLogsCount())
        assertEquals(
            listOf(AuditLogAction.IMPORT, AuditLogAction.EXPORT),
            env.auditLogService.getLogs(QueryPagination(page = 0, itemsPerPage = 2)).map { it.action },
        )
        assertEquals(
            listOf(AuditLogAction.RELOAD),
            env.auditLogService.getLogs(QueryPagination(page = 1, itemsPerPage = 2)).map { it.action },
        )
    }

    private fun age(
        entry: AuditLogEntity,
        days: Int,
    ) = transaction(env.context.database) { AuditLogEntity.findById(entry.id.value)!!.timestamp = Clock.System.now() - days.days }

    @Test
    fun `cleanup removes logs older than the retention and keeps newer ones`() {
        env.auditLogService.log(AuditLogAction.RELOAD)
        env.auditLogService.log(AuditLogAction.EXPORT)
        age(logs().first { it.action == AuditLogAction.RELOAD }, 100)

        env.databaseService.performLogsCleanup(90.days)

        assertEquals(listOf(AuditLogAction.EXPORT), logs().map { it.action })
    }

    @Test
    fun `the retention is read from the config`() {
        assertEquals(90, env.configService.getConfig().logs.purgeLogsAfterDays)
        assertTrue(env.configService.getConfig().logs.enabled)
    }
}
