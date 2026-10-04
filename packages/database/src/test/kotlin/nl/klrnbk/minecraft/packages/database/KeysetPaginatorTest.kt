package nl.klrnbk.minecraft.packages.database

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

object ItemTable : UuidTable("items") {
    val owner = integer("owner")
    val name = varchar("name", 50)
    val createdAt = timestamp("created_at")

    init {
        index(false, owner, createdAt)
    }
}

class Item(
    id: EntityID<Uuid>,
) : UuidEntity(id) {
    companion object : UuidEntityClass<Item>(ItemTable)

    var owner by ItemTable.owner
    var name by ItemTable.name
    var createdAt by ItemTable.createdAt
}

class KeysetPaginatorTest {
    private val directory = Files.createTempDirectory("keyset-test")
    private val datasource = BaseDatasource(DatabaseContext())

    init {
        datasource.connect(DatasourceConfig(type = DatasourceType.SQLITE, database = "test.db").also { it.dataDirectory = directory })
        transaction(datasource.database) { SchemaUtils.create(ItemTable) }
    }

    @AfterEach
    fun cleanup() {
        datasource.disconnect()
        directory.toFile().deleteRecursively()
    }

    private fun newestFirst(cache: PageCursorCache = PageCursorCache()) =
        KeysetPaginator(Item, ItemTable, ItemTable.createdAt, SortOrder.DESC, { it.createdAt }, Instant::toString, Instant::parse, cache)

    private fun byNameAscending(cache: PageCursorCache = PageCursorCache()) =
        KeysetPaginator(Item, ItemTable, ItemTable.name, SortOrder.ASC, { it.name }, { it }, { it }, cache)

    /**
     * Inserts items named item00, item01, ... Several items share a timestamp, the ids break those ties.
     */
    private fun insert(
        count: Int,
        owner: Int = 1,
        startIndex: Int = 0,
        timestampsPerSecond: Int = 5,
    ) = transaction(datasource.database) {
        repeat(count) {
            val index = startIndex + it
            Item.new {
                this.owner = owner
                this.name = "item%02d".format(index)
                this.createdAt = Instant.fromEpochSeconds(1_700_000_000L + index / timestampsPerSecond)
            }
        }
    }

    private fun page(
        paginator: KeysetPaginator<Item, *>,
        page: Int,
        size: Int = 4,
        scope: String = "all",
        filter: Op<Boolean> = Op.TRUE,
    ): List<String> = transaction(datasource.database) { paginator.findPage(scope, filter, page, size).map { it.name } }

    private fun <T> capturingSql(block: () -> T): Pair<T, List<String>> {
        val statements = mutableListOf<String>()
        val result =
            transaction(datasource.database) {
                addLogger(
                    object : SqlLogger {
                        override fun log(
                            context: StatementContext,
                            transaction: Transaction,
                        ) {
                            statements.add(context.sql(transaction))
                        }
                    },
                )
                block()
            }
        return result to statements
    }

    @Test
    fun `walking through every page returns each row exactly once, in order`() {
        insert(23)
        val paginator = byNameAscending()

        val all = (1..6).flatMap { page(paginator, it) }

        assertEquals((0 until 23).map { "item%02d".format(it) }, all)
    }

    @Test
    fun `rows with the same sort value are neither skipped nor repeated`() {
        insert(23, timestampsPerSecond = 10) // 10 rows per timestamp, pages of 4 cut through them
        val paginator = newestFirst()

        val names = (1..6).flatMap { page(paginator, it) }

        assertEquals(23, names.size)
        assertEquals(23, names.toSet().size)
        // Newest timestamps come first; within a timestamp the order only has to be stable.
        assertEquals((20..22).toSet(), names.take(3).map { it.removePrefix("item").toInt() }.toSet())
        assertEquals((0..9).toSet(), names.takeLast(10).map { it.removePrefix("item").toInt() }.toSet())
    }

    @Test
    fun `descending order returns the newest rows first`() {
        insert(12, timestampsPerSecond = 1)

        assertEquals(listOf("item11", "item10", "item09", "item08"), page(newestFirst(), 1))
        assertEquals(listOf("item03", "item02", "item01", "item00"), page(newestFirst(), 3))
    }

    @Test
    fun `a page past the end and an empty table give an empty page`() {
        assertEquals(emptyList<String>(), page(byNameAscending(), 1))

        insert(8)
        assertEquals(emptyList<String>(), page(byNameAscending(), 3))
        assertEquals(emptyList<String>(), page(byNameAscending(), 50))
        assertEquals(4, page(byNameAscending(), 2).size)
    }

    @Test
    fun `the filter is respected`() {
        insert(10, owner = 1)
        insert(10, owner = 2, startIndex = 10)

        val names = (1..4).flatMap { page(byNameAscending(), it, size = 3, scope = "owner:2", filter = ItemTable.owner eq 2) }

        assertEquals((10..19).map { "item%02d".format(it) }, names)
    }

    @Test
    fun `jumping straight to a page gives the same rows as walking there`() {
        insert(50)
        val walked = byNameAscending()
        (1..9).forEach { page(walked, it) }

        assertEquals(page(walked, 10), page(byNameAscending(), 10)) // cold cache
        assertEquals(page(walked, 7), page(byNameAscending(), 7))
    }

    @Test
    fun `moving through pages and back never uses an offset`() {
        insert(60)
        val paginator = byNameAscending()

        val (_, statements) =
            capturingSql {
                (1..15).forEach { paginator.findPage("all", Op.TRUE, it, 4) }
                (14 downTo 2).forEach { paginator.findPage("all", Op.TRUE, it, 4) }
            }

        assertTrue(statements.isNotEmpty())
        assertFalse(statements.any { it.contains("OFFSET", ignoreCase = true) }, statements.joinToString("\n"))
    }

    @Test
    fun `jumping to an unknown page looks it up once, over the sort and id columns only`() {
        insert(60)

        val (_, statements) = capturingSql { byNameAscending().findPage("all", Op.TRUE, 12, 4) }

        val offsets = statements.filter { it.contains("OFFSET", ignoreCase = true) }
        assertEquals(1, offsets.size, statements.joinToString("\n"))
        assertTrue(offsets.single().contains("LIMIT 1 OFFSET 43"), offsets.single())
        assertFalse(offsets.single().contains("created_at"), "only the sort column and id are read: ${offsets.single()}")
        assertFalse(offsets.single().contains("owner"), offsets.single())
    }

    @Test
    fun `a looked up position is remembered`() {
        insert(60)
        val paginator = byNameAscending()
        paginator.also { page(it, 12) }

        val (_, statements) = capturingSql { paginator.findPage("all", Op.TRUE, 12, 4) }

        assertFalse(statements.any { it.contains("OFFSET", ignoreCase = true) })
    }

    @Test
    fun `rows added at the front while paging do not shift the next page`() {
        insert(20, timestampsPerSecond = 1)
        val paginator = newestFirst()
        val first = page(paginator, 1)

        insert(5, startIndex = 100, timestampsPerSecond = 1) // five new newest rows
        val second = page(paginator, 2)

        // With OFFSET the new rows would push four of the first page's rows onto page two.
        assertEquals(listOf("item19", "item18", "item17", "item16"), first)
        assertEquals(listOf("item15", "item14", "item13", "item12"), second)
    }

    @Test
    fun `remembered positions expire`() {
        insert(30)
        var now = 0L
        val cache = PageCursorCache(ttl = 1.minutes, nanoTime = { now })
        val paginator = byNameAscending(cache)
        page(paginator, 1)

        now += 30.seconds.inWholeNanoseconds
        assertFalse(capturingSql { paginator.findPage("all", Op.TRUE, 2, 4) }.second.any { it.contains("OFFSET") })

        now += 2.minutes.inWholeNanoseconds
        assertTrue(capturingSql { paginator.findPage("all", Op.TRUE, 3, 4) }.second.any { it.contains("OFFSET") })
    }

    @Test
    fun `scopes do not share positions`() {
        insert(10, owner = 1)
        insert(10, owner = 2, startIndex = 10)
        val paginator = byNameAscending()

        page(paginator, 1, size = 3, scope = "owner:1", filter = ItemTable.owner eq 1)
        val other = page(paginator, 2, size = 3, scope = "owner:2", filter = ItemTable.owner eq 2)

        assertEquals(listOf("item13", "item14", "item15"), other)
    }

    @Test
    fun `the cache drops the least recently used entries`() {
        val cache = PageCursorCache(maxEntries = 2)
        val cursor = KeysetCursor("x", Uuid.random())

        cache.put("a", 2, cursor)
        cache.put("a", 3, cursor)
        cache.get("a", 2)
        cache.put("a", 4, cursor)

        assertEquals(cursor, cache.get("a", 2))
        assertEquals(null, cache.get("a", 3))
        assertEquals(cursor, cache.get("a", 4))
    }
}
