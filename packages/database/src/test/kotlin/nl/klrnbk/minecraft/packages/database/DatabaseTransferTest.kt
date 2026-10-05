package nl.klrnbk.minecraft.packages.database

import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransfer
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class Kind { SMALL, LARGE }

object OwnerTable : UuidTable("owners") {
    val name = varchar("name", 50)
    val joined = timestamp("joined")
}

object PetTable : UuidTable("pets") {
    val owner = uuid("owner")
    val kind = enumerationByName<Kind>("kind", 20)
    val age = integer("age")
    val nickname = varchar("nickname", 50).nullable()
}

class DatabaseTransferTest {
    private val directory = Files.createTempDirectory("transfer-test")
    private val tables = listOf(OwnerTable, PetTable)

    private val sourceContext = DatabaseContext()
    private val targetContext = DatabaseContext()
    private val source = BaseDatasource(sourceContext)
    private val target = BaseDatasource(targetContext)

    init {
        source.connect(DatasourceConfig(type = DatasourceType.SQLITE, database = "source.db").also { it.dataDirectory = directory })
        target.connect(DatasourceConfig(type = DatasourceType.SQLITE, database = "target.db").also { it.dataDirectory = directory })
        transaction(source.database) { SchemaUtils.create(*tables.toTypedArray()) }
        transaction(target.database) { SchemaUtils.create(*tables.toTypedArray()) }
    }

    @AfterEach
    fun cleanup() {
        source.disconnect()
        target.disconnect()
        directory.toFile().deleteRecursively()
    }

    private val ownerId = Uuid.random()

    private fun seedSource(pets: Int = 3) =
        transaction(source.database) {
            OwnerTable.insert {
                it[id] = ownerId
                it[name] = "Daan \"the\" Owner, ünïcode\nnewline"
                it[joined] = Instant.parse("2026-01-02T03:04:05.678Z")
            }
            repeat(pets) { index ->
                PetTable.insert {
                    it[owner] = ownerId
                    it[kind] = if (index % 2 == 0) Kind.SMALL else Kind.LARGE
                    it[age] = index
                    it[nickname] = if (index == 0) null else "pet$index"
                }
            }
        }

    private fun dump(context: DatabaseContext) =
        transaction(context.database) {
            tables.associate { table -> table.tableName to table.selectAll().map { row -> table.columns.map { row[it] } }.toSet() }
        }

    @Test
    fun `an export imports into another database unchanged`() {
        seedSource()
        val file = directory.resolve("export.zip")

        val exported = DatabaseTransfer(sourceContext).export(tables, file)
        val imported = DatabaseTransfer(targetContext).import(tables, file)

        assertEquals(mapOf("owners" to 1L, "pets" to 3L), exported.rows)
        assertEquals(exported, imported)
        assertEquals(dump(sourceContext), dump(targetContext))
    }

    @Test
    fun `more rows than one batch survive`() {
        seedSource(pets = 1_234)
        val file = directory.resolve("export.zip")

        DatabaseTransfer(sourceContext).export(tables, file)
        val imported = DatabaseTransfer(targetContext).import(tables, file)

        assertEquals(1_234L, imported.rows.getValue("pets"))
        assertEquals(dump(sourceContext), dump(targetContext))
    }

    @Test
    fun `an empty database exports and imports`() {
        val file = directory.resolve("export.zip")

        val exported = DatabaseTransfer(sourceContext).export(tables, file)

        assertEquals(0L, exported.totalRows)
        assertEquals(exported, DatabaseTransfer(targetContext).import(tables, file))
    }

    @Test
    fun `export refuses to overwrite a file`() {
        val file = Files.createFile(directory.resolve("export.zip"))

        assertThrows(DatabaseTransferException::class.java) { DatabaseTransfer(sourceContext).export(tables, file) }
        assertEquals(0, Files.size(file))
    }

    @Test
    fun `import refuses tables that already have rows and changes nothing`() {
        seedSource()
        val file = directory.resolve("export.zip")
        DatabaseTransfer(sourceContext).export(tables, file)
        transaction(target.database) {
            PetTable.insert {
                it[owner] = ownerId
                it[kind] = Kind.SMALL
                it[age] = 1
                it[nickname] = null
            }
        }

        val exception = assertThrows(DatabaseTransferException::class.java) { DatabaseTransfer(targetContext).import(tables, file) }

        assertTrue(exception.message!!.contains("pets"))
        assertEquals(0, transaction(target.database) { OwnerTable.selectAll().count() })
        assertEquals(1, transaction(target.database) { PetTable.selectAll().count() })
    }

    @Test
    fun `a failing import rolls back everything`() {
        seedSource()
        val file = directory.resolve("broken.zip")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            val good = DatabaseTransfer(sourceContext)
            val valid = directory.resolve("valid.zip").also { good.export(tables, it) }
            java.util.zip.ZipFile(valid.toFile()).use { original ->
                original.entries().asSequence().forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.name))
                    val content = original.getInputStream(entry).readBytes().decodeToString()
                    // Corrupt the second table, after the first one has been inserted.
                    zip.write((if (entry.name.endsWith("pets.ndjson")) content.replace("SMALL", "HUGE") else content).toByteArray())
                    zip.closeEntry()
                }
            }
        }

        assertThrows(DatabaseTransferException::class.java) { DatabaseTransfer(targetContext).import(tables, file) }

        assertEquals(0, transaction(target.database) { OwnerTable.selectAll().count() })
    }

    @Test
    fun `a file that isn't an export is rejected`() {
        val file = directory.resolve("nonsense.zip")
        Files.writeString(file, "not a zip")

        assertThrows(DatabaseTransferException::class.java) { DatabaseTransfer(targetContext).import(tables, file) }
    }

    @Test
    fun `a file missing a table is rejected`() {
        val file = directory.resolve("export.zip")
        DatabaseTransfer(sourceContext).export(listOf(OwnerTable), file)

        val exception = assertThrows(DatabaseTransferException::class.java) { DatabaseTransfer(targetContext).import(tables, file) }

        assertTrue(exception.message!!.contains("pets"))
        assertFalse(transaction(target.database) { OwnerTable.selectAll().any() })
    }

    @Test
    fun `duplicate tables are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseTransfer(sourceContext).export(listOf(OwnerTable, OwnerTable), directory.resolve("export.zip"))
        }
    }
}
