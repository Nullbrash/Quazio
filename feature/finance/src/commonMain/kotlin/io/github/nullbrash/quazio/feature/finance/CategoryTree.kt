package io.github.nullbrash.quazio.feature.finance

/** Строка категории из базы — без зависимости от сгенерированных классов, для теста. */
internal data class CategoryRow(
    val id: String,
    val parentId: String?,
    val name: String,
    val kind: CategoryKind,
    val color: Long?,
    val sortKey: String,
)

/**
 * Плоский список → дерево в порядке обхода (родитель, затем его дети), с глубиной,
 * путём и цветом группы для детей без своего цвета. Строки с потерянным родителем
 * (родитель удалён) поднимаются в корень — категория не должна пропасть из списка.
 */
internal fun buildCategoryTree(rows: List<CategoryRow>): List<CategoryNode> {
    val ids = rows.mapTo(HashSet()) { it.id }
    val byParent = rows.groupBy { row -> row.parentId?.takeIf { it in ids } }
    val out = ArrayList<CategoryNode>(rows.size)
    fun walk(parentId: String?, depth: Int, inheritedColor: Long?, parentPath: String?) {
        val children = byParent[parentId].orEmpty().sortedWith(compareBy({ it.sortKey }, { it.name.lowercase() }))
        for (row in children) {
            val color = row.color ?: inheritedColor
            val path = if (parentPath == null) row.name else "$parentPath / ${row.name}"
            out += CategoryNode(row.id, row.parentId?.takeIf { it in ids }, row.name, row.kind, color, depth, path)
            if (depth < MAX_DEPTH) walk(row.id, depth + 1, color, path)
        }
    }
    walk(null, 0, null, null)
    return out
}

// Защита от зацикливания, если синхронизация когда-нибудь принесёт кольцо родителей.
private const val MAX_DEPTH = 16
