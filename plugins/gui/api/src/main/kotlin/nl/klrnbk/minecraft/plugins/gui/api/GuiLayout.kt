package nl.klrnbk.minecraft.plugins.gui.api

/**
 * The shape of a [Gui]'s container.
 *
 * Only layouts that both the Java client and Geyser (Bedrock) render as a plain,
 * freely fillable container are offered. Special-purpose containers (anvil, furnace,
 * villager trade, ...) are deliberately absent: they carry client-side behaviour that
 * a proxy cannot emulate.
 */
public sealed class GuiLayout(
    /** Number of slots that belong to the GUI itself (excluding the player's inventory). */
    public val size: Int,
    /** Number of slots per row, used for row/column math. */
    public val columns: Int,
) {
    /** A chest-style container with 1 to 6 rows of 9 slots (Java `generic_9xN`). */
    public class Chest(
        public val rows: Int,
    ) : GuiLayout(rows * COLUMNS, COLUMNS) {
        init {
            require(rows in MIN_ROWS..MAX_ROWS) { "A chest GUI needs $MIN_ROWS..$MAX_ROWS rows, got $rows" }
        }

        override fun equals(other: Any?): Boolean = other is Chest && other.rows == rows

        override fun hashCode(): Int = rows

        override fun toString(): String = "Chest(rows=$rows)"

        public companion object {
            public const val COLUMNS: Int = 9
            public const val MIN_ROWS: Int = 1
            public const val MAX_ROWS: Int = 6
        }
    }

    /** A hopper container: a single row of 5 slots. */
    public data object Hopper : GuiLayout(size = 5, columns = 5)

    /** A dispenser/dropper style 3x3 container. */
    public data object Dispenser : GuiLayout(size = 9, columns = 3)

    /** Converts a `(row, column)` pair (both zero based) to a slot index, validating the bounds. */
    public fun slot(
        row: Int,
        column: Int,
    ): Int {
        require(column in 0 until columns) { "column $column is outside 0..${columns - 1}" }
        val slot = row * columns + column
        require(row >= 0 && slot < size) { "row $row is outside this layout" }
        return slot
    }

    public companion object {
        /** Shorthand for [Chest]. */
        public fun chest(rows: Int): GuiLayout = Chest(rows)
    }
}
