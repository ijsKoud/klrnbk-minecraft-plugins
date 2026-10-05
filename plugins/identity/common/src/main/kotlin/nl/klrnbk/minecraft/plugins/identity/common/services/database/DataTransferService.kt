package nl.klrnbk.minecraft.plugins.identity.common.services.database

import com.google.inject.Inject
import com.google.inject.Singleton
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransfer
import nl.klrnbk.minecraft.packages.database.transfer.DatabaseTransferException
import nl.klrnbk.minecraft.packages.database.transfer.TransferResult
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

/**
 * Exports the plugin's data to files in the `exports` folder of the data directory, and imports them again.
 */
@Singleton
class DataTransferService
    @Inject
    constructor(
        private val logger: Logger,
        private val databaseTransfer: DatabaseTransfer,
    ) {
        /**
         * Exports all data to a new file in the `exports` folder of [dataDirectory].
         */
        @Synchronized
        fun exportData(dataDirectory: Path): ExportOutcome {
            val directory = Files.createDirectories(exportsDirectory(dataDirectory))
            val file = directory.resolve("identity-export-${TIMESTAMP_FORMAT.format(LocalDateTime.now())}.zip")

            logger.info("Exporting data to ${file.name}...")
            val result = databaseTransfer.export(DatabaseService.TABLES, file)
            logger.info("Exported ${result.totalRows} rows to ${file.name}.")

            return ExportOutcome(file.name, result)
        }

        /**
         * Imports a file from the `exports` folder of [dataDirectory] into the (empty) tables.
         */
        @Synchronized
        fun importData(
            dataDirectory: Path,
            fileName: String,
        ): TransferResult {
            val directory = exportsDirectory(dataDirectory)
            // Only plain names, so a command can't be used to read files elsewhere on the machine.
            if (Path.of(fileName).fileName?.toString() != fileName) {
                throw DatabaseTransferException("Give the name of a file in the exports folder, not a path.")
            }

            val file = directory.resolve(fileName)
            if (!Files.isRegularFile(file)) throw DatabaseTransferException("There is no file named $fileName in the exports folder.")

            logger.info("Importing data from $fileName...")
            val result = databaseTransfer.import(DatabaseService.TABLES, file)
            logger.info("Imported ${result.totalRows} rows from $fileName.")

            return result
        }

        fun getExportSuggestions(dataDirectory: Path): List<String> {
            val directory = exportsDirectory(dataDirectory)
            if (!Files.isDirectory(directory)) return emptyList()

            return Files.list(directory).use { files -> files.map { it.name }.filter { it.endsWith(".zip") }.sorted().toList() }
        }

        private fun exportsDirectory(dataDirectory: Path): Path = dataDirectory.resolve("exports")

        data class ExportOutcome(
            val fileName: String,
            val result: TransferResult,
        )

        private companion object {
            val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        }
    }
