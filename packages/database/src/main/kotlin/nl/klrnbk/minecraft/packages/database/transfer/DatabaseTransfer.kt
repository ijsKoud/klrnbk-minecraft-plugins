package nl.klrnbk.minecraft.packages.database.transfer

import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nl.klrnbk.minecraft.packages.database.BaseRepository
import nl.klrnbk.minecraft.packages.database.DatabaseContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.io.BufferedReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

/**
 * Exports tables to a file and imports such a file again, to move data between databases (for example from SQLite
 * to PostgreSQL) or to take a backup.
 *
 * An export is a zip with a `manifest.json` and one `tables/<name>.ndjson` per table, holding one JSON object per row.
 * Rows are streamed, so a big table never has to fit in memory. Values are stored portably (see [ColumnCodec]),
 * so the database type of the source and the target don't have to match.
 *
 * The tables are passed in by the caller, who has to list them parents first (the order in which they can be
 * inserted). An import is a single transaction: it either restores everything or changes nothing, and it
 * refuses to run when a table already holds rows or when the file doesn't match the table definitions.
 */
@Singleton
class DatabaseTransfer
    @Inject
    constructor(
        context: DatabaseContext,
    ) : BaseRepository(context) {
        private val json = Json

        /**
         * Writes [tables] to a new file at [target]. An existing file is never overwritten.
         *
         * @throws DatabaseTransferException if [target] exists or a table can't be exported.
         */
        fun export(
            tables: List<IdTable<*>>,
            target: Path,
        ): TransferResult {
            validate(tables)
            tables.forEach { table -> table.columns.forEach(ColumnCodec::checkSupported) }

            val output =
                try {
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                } catch (_: java.nio.file.FileAlreadyExistsException) {
                    throw DatabaseTransferException("The file $target already exists.")
                }

            try {
                return ZipOutputStream(output).use { zip -> execute { writeTables(tables, zip) } }
            } catch (exception: Throwable) {
                Files.deleteIfExists(target)
                throw exception
            }
        }

        /**
         * Restores the file at [source] into [tables], which have to be empty.
         *
         * @throws DatabaseTransferException if the file is unreadable or doesn't match [tables], or a table has rows.
         */
        fun import(
            tables: List<IdTable<*>>,
            source: Path,
        ): TransferResult {
            validate(tables)
            tables.forEach { table -> table.columns.forEach(ColumnCodec::checkSupported) }

            try {
                ZipFile(source.toFile()).use { zip ->
                    val manifest = readManifest(zip)
                    checkMatches(tables, manifest)

                    return execute { readTables(tables, manifest, zip) }
                }
            } catch (exception: IOException) {
                throw DatabaseTransferException("Could not read $source: ${exception.message}", exception)
            }
        }

        private fun writeTables(
            tables: List<IdTable<*>>,
            zip: ZipOutputStream,
        ): TransferResult {
            val counts = linkedMapOf<String, Long>()

            for (table in tables) {
                zip.putNextEntry(ZipEntry(TransferManifest.entryNameOf(table.tableName)))
                val writer = zip.bufferedWriter()
                var rows = 0L

                // Ordered by id so an export is stable, streamed so the table isn't loaded at once.
                table.selectAll().orderBy(table.id).fetchSize(FETCH_SIZE).forEach { row ->
                    val values = table.columns.associate { it.name to ColumnCodec.encode(it.columnType, row[it]) }
                    writer.write(json.encodeToString(JsonObject.serializer(), JsonObject(values)))
                    writer.newLine()
                    rows++
                }

                writer.flush()
                zip.closeEntry()
                counts[table.tableName] = rows
            }

            val manifest =
                TransferManifest(
                    formatVersion = TransferManifest.CURRENT_FORMAT_VERSION,
                    exportedAt = Clock.System.now().toString(),
                    tables = tables.map { TableManifest(it.tableName, it.columns.map(Column<*>::name), counts.getValue(it.tableName)) },
                )
            zip.putNextEntry(ZipEntry(TransferManifest.FILE_NAME))
            zip.write(json.encodeToString(TransferManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()

            return TransferResult(counts)
        }

        private fun readTables(
            tables: List<IdTable<*>>,
            manifest: TransferManifest,
            zip: ZipFile,
        ): TransferResult {
            val notEmpty = tables.filter { !it.selectAll().limit(1).empty() }
            if (notEmpty.isNotEmpty()) {
                throw DatabaseTransferException(
                    "Can only import into empty tables, but these already have rows: ${notEmpty.joinToString { it.tableName }}.",
                )
            }

            val counts = linkedMapOf<String, Long>()

            for (table in tables) {
                val entryName = TransferManifest.entryNameOf(table.tableName)
                val entry = zip.getEntry(entryName) ?: throw DatabaseTransferException("The file has no $entryName.")

                val rows = zip.getInputStream(entry).bufferedReader().use { importRows(table, entryName, it) }
                val expected = manifest.tables.first { it.name == table.tableName }.rows
                if (rows != expected) {
                    throw DatabaseTransferException("$entryName has $rows rows, but the manifest says $expected. The file is incomplete.")
                }

                counts[table.tableName] = rows
            }

            return TransferResult(counts)
        }

        private fun importRows(
            table: IdTable<*>,
            entryName: String,
            reader: BufferedReader,
        ): Long {
            var rows = 0L
            var lineNumber = 0
            val batch = ArrayList<Map<Column<*>, Any?>>(BATCH_SIZE)

            fun flush() {
                if (batch.isEmpty()) return

                @Suppress("UNCHECKED_CAST")
                table.batchInsert(batch, shouldReturnGeneratedValues = false) { values ->
                    values.forEach { (column, value) -> this[column as Column<Any?>] = value }
                }
                batch.clear()
            }

            reader.forEachLine { line ->
                lineNumber++
                if (line.isBlank()) return@forEachLine

                try {
                    val obj = json.parseToJsonElement(line) as? JsonObject ?: throw DatabaseTransferException("Expected an object.")
                    batch +=
                        table.columns.associateWith { column ->
                            val element = obj[column.name] ?: throw DatabaseTransferException("Missing column '${column.name}'.")
                            ColumnCodec.decode(column.columnType, element, table)
                        }
                } catch (exception: SerializationException) {
                    throw DatabaseTransferException("$entryName line $lineNumber is not valid JSON.", exception)
                } catch (exception: DatabaseTransferException) {
                    throw DatabaseTransferException("$entryName line $lineNumber: ${exception.message}", exception)
                } catch (exception: IllegalArgumentException) {
                    throw DatabaseTransferException("$entryName line $lineNumber: ${exception.message}", exception)
                }

                rows++
                if (batch.size >= BATCH_SIZE) flush()
            }
            flush()

            return rows
        }

        private fun readManifest(zip: ZipFile): TransferManifest {
            val entry = zip.getEntry(TransferManifest.FILE_NAME) ?: throw DatabaseTransferException("Not an export file: it has no ${TransferManifest.FILE_NAME}.")

            val manifest =
                try {
                    json.decodeFromString(TransferManifest.serializer(), zip.getInputStream(entry).readBytes().decodeToString())
                } catch (exception: SerializationException) {
                    throw DatabaseTransferException("The manifest of the file is invalid.", exception)
                }

            if (manifest.formatVersion != TransferManifest.CURRENT_FORMAT_VERSION) {
                throw DatabaseTransferException(
                    "The file has format version ${manifest.formatVersion}, this version only reads version ${TransferManifest.CURRENT_FORMAT_VERSION}.",
                )
            }

            return manifest
        }

        private fun checkMatches(
            tables: List<IdTable<*>>,
            manifest: TransferManifest,
        ) {
            for (table in tables) {
                val exported =
                    manifest.tables.firstOrNull { it.name == table.tableName }
                        ?: throw DatabaseTransferException("The file has no data for table '${table.tableName}'.")

                val expectedColumns = table.columns.map(Column<*>::name).toSet()
                if (exported.columns.toSet() != expectedColumns) {
                    throw DatabaseTransferException(
                        "The columns of table '${table.tableName}' differ: file has ${exported.columns.sorted()}, expected ${expectedColumns.sorted()}.",
                    )
                }
            }
        }

        private fun validate(tables: List<IdTable<*>>) {
            require(tables.isNotEmpty()) { "No tables given." }

            val duplicate = tables.groupBy { it.tableName }.entries.firstOrNull { it.value.size > 1 }
            require(duplicate == null) { "Table ${duplicate?.key} is listed more than once." }
        }

        private companion object {
            const val FETCH_SIZE = 1_000
            const val BATCH_SIZE = 500
        }
    }
