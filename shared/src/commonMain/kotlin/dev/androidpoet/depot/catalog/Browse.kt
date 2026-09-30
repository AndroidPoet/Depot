package dev.androidpoet.depot.catalog

enum class SortOrder(val label: String) {
    Updated("Recently updated"),
    Newest("Newest"),
    Name("Name"),
}

data class BrowseQuery(
    val text: String = "",
    val category: String? = null,
    val sort: SortOrder = SortOrder.Updated,
)

fun List<CatalogApp>.browse(query: BrowseQuery): List<CatalogApp> {
    val needle = query.text.trim().lowercase()
    val sorted = asSequence()
        .filter { query.category == null || query.category in it.categories }
        .filter { needle.isEmpty() || it.matchRank(needle) != null }
        .sortedWith(query.sort.comparator)
        .toList()
    if (needle.isEmpty()) return sorted
    return sorted.sortedBy { it.matchRank(needle) }
}

fun List<CatalogApp>.categoryCounts(): List<Pair<String, Int>> =
    flatMap { it.categories }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedBy { it.first.lowercase() }

private fun CatalogApp.matchRank(needle: String): Int? {
    val lowerName = name.lowercase()
    return when {
        lowerName.startsWith(needle) -> 0
        needle in lowerName -> 1
        needle in summary.lowercase() || needle in packageName.lowercase() -> 2
        else -> null
    }
}

private val SortOrder.comparator: Comparator<CatalogApp>
    get() = when (this) {
        SortOrder.Updated -> compareByDescending { it.lastUpdated }
        SortOrder.Newest -> compareByDescending { it.added }
        SortOrder.Name -> compareBy { it.name.lowercase() }
    }
