package nl.klrnbk.minecraft.packages.database.transfer

import kotlinx.serialization.Serializable

/**
 * Describes the contents of an export file (`manifest.json`).
 */
@Serializable
data class TransferManifest(
    val formatVersion: Int,
    val exportedAt: String,
    val tables: List<TableManifest>,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val FILE_NAME = "manifest.json"

        fun entryNameOf(table: String) = "tables/$table.ndjson"
    }
}

@Serializable
data class TableManifest(
    val name: String,
    val columns: List<String>,
    val rows: Long,
)

/**
 * The number of rows written or read per table, in the order of the tables.
 */
data class TransferResult(
    val rows: Map<String, Long>,
) {
    val totalRows: Long get() = rows.values.sum()
}
