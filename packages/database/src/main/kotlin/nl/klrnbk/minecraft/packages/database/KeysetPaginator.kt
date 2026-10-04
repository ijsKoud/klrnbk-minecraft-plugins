package nl.klrnbk.minecraft.packages.database

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Pages through a table without `LIMIT ... OFFSET`.
 *
 * `OFFSET n` makes the database walk over and throw away n rows for every page request, so page 100 costs a
 * hundred times as much as page 1. Seek ("keyset") pagination instead asks for the rows *after* the last row of
 * the previous page (`WHERE sort > :last OR (sort = :last AND id > :lastId) ORDER BY sort, id LIMIT n`), which an
 * index on the sort column answers directly, no matter how deep the page is.
 *
 * Callers still talk in page numbers. A page's starting position is remembered in [cache] when a page is read,
 * so moving to the next page, back to a visited page, or reloading a page never needs an offset. Only when a page
 * is requested whose position is unknown (the first request after a restart, or jumping straight to page 100)
 * the position is looked up once with an offset over just the sort and id columns, which the index covers, and
 * the rows themselves are still read by seeking.
 *
 * Create one paginator per query shape and keep it around (the repositories are singletons), because it owns
 * the cache. Every method has to be called inside a transaction. Add an index over (the columns of the filter,
 * the sort column) so the seek is an index range scan.
 *
 * @param sort The expression to order by.
 * @param order The sort direction. Ties are broken by id in the same direction.
 * @param sortValueOf Reads the sort value of an entity, used to build cursors. Must match what [sort] produces.
 * @param encode Turns a sort value into text for the cursor.
 * @param decode Turns cursor text back into a sort value.
 */
class KeysetPaginator<E : UuidEntity, S : Comparable<S>>(
    private val entityClass: UuidEntityClass<E>,
    private val table: UuidTable,
    private val sort: ExpressionWithColumnType<S>,
    private val order: SortOrder,
    private val sortValueOf: (E) -> S,
    private val encode: (S) -> String,
    private val decode: (String) -> S,
    private val cache: PageCursorCache = PageCursorCache(),
) {
    /**
     * Reads one page.
     *
     * @param scope Names the query (filter) the page belongs to, e.g. `"player:<id>"`. Pages of different scopes
     * must not share a name, since the remembered positions are per scope.
     * @param filter The filter of the query, [Op.TRUE] for none.
     * @param page The page to read, starting from 1.
     * @param size The number of rows per page.
     * @return The rows of the page, an empty list if the page is past the end.
     */
    fun findPage(
        scope: String,
        filter: Op<Boolean>,
        page: Int,
        size: Int,
    ): List<E> {
        require(page >= 1) { "page starts at 1" }
        require(size >= 1) { "size must be positive" }

        val after =
            if (page == 1) {
                null
            } else {
                cache.get(scope, page)
                    ?: lookUpCursorBefore(filter, page, size)?.also { cache.put(scope, page, it) }
                    ?: return emptyList()
            }

        val rows = fetch(filter, after, size)
        // A full page means there is probably a next one, remember where it starts.
        if (rows.size == size) cache.put(scope, page + 1, cursorOf(rows.last()))

        return rows
    }

    private fun fetch(
        filter: Op<Boolean>,
        after: KeysetCursor?,
        size: Int,
    ): List<E> {
        val condition = if (after == null) filter else filter and seekCondition(after)

        return entityClass
            .find { condition }
            .orderBy(sort to order, table.id to order)
            .limit(size)
            .toList()
    }

    private fun seekCondition(cursor: KeysetCursor): Op<Boolean> {
        val value = decode(cursor.sort)
        val id = cursor.id

        return if (order == SortOrder.ASC) {
            (sort greater value) or ((sort eq value) and (table.id greater id))
        } else {
            (sort less value) or ((sort eq value) and (table.id less id))
        }
    }

    /**
     * The cursor of the row just before the first row of [page]. The only place an offset is used, and it
     * only reads the sort and id columns.
     */
    private fun lookUpCursorBefore(
        filter: Op<Boolean>,
        page: Int,
        size: Int,
    ): KeysetCursor? {
        val position = (page - 1).toLong() * size - 1

        val row =
            table
                .select(sort as Expression<S>, table.id)
                .where { filter }
                .orderBy(sort to order, table.id to order)
                .limit(1)
                .offset(position)
                .firstOrNull() ?: return null

        return KeysetCursor(encode(row[sort]), row[table.id].value)
    }

    private fun cursorOf(entity: E) = KeysetCursor(encode(sortValueOf(entity)), entity.id.value)
}
