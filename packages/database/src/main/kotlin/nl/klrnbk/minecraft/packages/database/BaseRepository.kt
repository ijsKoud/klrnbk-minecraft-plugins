package nl.klrnbk.minecraft.packages.database

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

open class BaseRepository(
    private val context: DatabaseContext,
) {
    protected fun <T> execute(block: Transaction.() -> T): T =
        transaction(context.database) {
            block()
        }
}
