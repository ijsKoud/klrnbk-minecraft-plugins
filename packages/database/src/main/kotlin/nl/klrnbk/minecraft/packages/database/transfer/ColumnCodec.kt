package nl.klrnbk.minecraft.packages.database.transfer

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.EnumerationColumnType
import org.jetbrains.exposed.v1.core.EnumerationNameColumnType
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ShortColumnType
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.UuidColumnType
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Converts column values to and from the JSON used in an export file.
 *
 * The file only holds portable values (text, numbers, booleans, UUIDs and timestamps as text, enums by name),
 * never anything database specific, so an export of one database type can be imported into another.
 * Column types that aren't listed here are rejected up front instead of being exported in a way that
 * couldn't be restored.
 */
internal object ColumnCodec {
    fun checkSupported(column: Column<*>) {
        if (!isSupported(column.columnType)) {
            throw DatabaseTransferException(
                "Column '${column.table.tableName}.${column.name}' has type ${column.columnType::class.simpleName}, which can't be exported.",
            )
        }
    }

    fun encode(
        columnType: IColumnType<*>,
        value: Any?,
    ): JsonElement {
        if (value == null) return JsonNull

        return when (columnType) {
            is EntityIDColumnType<*> -> encode(columnType.idColumn.columnType, (value as EntityID<*>).value)
            is UuidColumnType -> JsonPrimitive((value as Uuid).toString())
            is StringColumnType -> JsonPrimitive(value as String)
            is IntegerColumnType -> JsonPrimitive(value as Int)
            is LongColumnType -> JsonPrimitive(value as Long)
            is ShortColumnType -> JsonPrimitive(value as Short)
            is BooleanColumnType -> JsonPrimitive(value as Boolean)
            is DoubleColumnType -> JsonPrimitive(value as Double)
            is FloatColumnType -> JsonPrimitive(value as Float)
            is KotlinInstantColumnType -> JsonPrimitive((value as Instant).toString())
            is EnumerationNameColumnType<*> -> JsonPrimitive((value as Enum<*>).name)
            is EnumerationColumnType<*> -> JsonPrimitive((value as Enum<*>).name)
            else -> throw DatabaseTransferException("Unsupported column type ${columnType::class.simpleName}.")
        }
    }

    /**
     * @param table Needed to wrap values of the id column into an [EntityID].
     */
    fun decode(
        columnType: IColumnType<*>,
        element: JsonElement,
        table: IdTable<*>,
    ): Any? {
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: throw DatabaseTransferException("Expected a plain value but found $element.")

        @Suppress("UNCHECKED_CAST")
        return when (columnType) {
            is EntityIDColumnType<*> -> EntityID(decode(columnType.idColumn.columnType, element, table) as Comparable<Any>, table as IdTable<Comparable<Any>>)
            is UuidColumnType -> Uuid.parse(text(primitive))
            is StringColumnType -> text(primitive)
            is IntegerColumnType -> primitive.intOrNull ?: invalid(primitive)
            is LongColumnType -> primitive.longOrNull ?: invalid(primitive)
            is ShortColumnType -> primitive.intOrNull?.toShort() ?: invalid(primitive)
            is BooleanColumnType -> primitive.booleanOrNull ?: invalid(primitive)
            is DoubleColumnType -> primitive.doubleOrNull ?: invalid(primitive)
            is FloatColumnType -> primitive.floatOrNull ?: invalid(primitive)
            is KotlinInstantColumnType -> Instant.parse(text(primitive))
            is EnumerationNameColumnType<*> -> enumByName(columnType.klass.java, text(primitive))
            is EnumerationColumnType<*> -> enumByName(columnType.klass.java, text(primitive))
            else -> throw DatabaseTransferException("Unsupported column type ${columnType::class.simpleName}.")
        }
    }

    private fun isSupported(columnType: IColumnType<*>): Boolean =
        when (columnType) {
            is EntityIDColumnType<*> -> isSupported(columnType.idColumn.columnType)
            is UuidColumnType,
            is StringColumnType,
            is IntegerColumnType,
            is LongColumnType,
            is ShortColumnType,
            is BooleanColumnType,
            is DoubleColumnType,
            is FloatColumnType,
            is KotlinInstantColumnType,
            is EnumerationNameColumnType<*>,
            is EnumerationColumnType<*>,
            -> true
            else -> false
        }

    private fun text(primitive: JsonPrimitive): String = primitive.contentOrNull ?: invalid(primitive)

    private fun enumByName(
        type: Class<*>,
        name: String,
    ): Any =
        type.enumConstants.firstOrNull { (it as Enum<*>).name == name }
            ?: throw DatabaseTransferException("'$name' is not a value of ${type.simpleName}.")

    private fun invalid(primitive: JsonPrimitive): Nothing = throw DatabaseTransferException("Unexpected value $primitive.")
}
