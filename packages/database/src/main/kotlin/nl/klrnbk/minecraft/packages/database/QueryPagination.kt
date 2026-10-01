package nl.klrnbk.minecraft.packages.database

data class QueryPagination(
    /**
     * The page number to retrieve, starting from 0.
     */
    val page: Int = 0,
    /**
     * The number of items to retrieve per page. Defaults to 25.
     */
    val itemsPerPage: Int = 25,
)
