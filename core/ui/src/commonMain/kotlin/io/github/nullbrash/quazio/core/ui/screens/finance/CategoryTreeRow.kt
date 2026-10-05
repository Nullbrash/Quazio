package io.github.nullbrash.quazio.core.ui.screens.finance

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.feature.finance.CategoryNode

/**
 * Линии дерева (как в проводнике): для каждой строки — продолжаются ли вертикали
 * предков ([ancestorsContinue], по уровням с 1-го) и последний ли это ребёнок.
 */
internal data class TreeGuide(val ancestorsContinue: List<Boolean>, val isLast: Boolean, val hasChildren: Boolean)

internal fun treeGuides(nodes: List<CategoryNode>): Map<String, TreeGuide> {
    val children = nodes.groupBy { it.parentId }
    val out = HashMap<String, TreeGuide>(nodes.size)
    fun walk(parent: String?, cont: List<Boolean>) {
        val list = children[parent].orEmpty()
        list.forEachIndexed { i, n ->
            val last = i == list.lastIndex
            out[n.id] = TreeGuide(cont, last, children[n.id].orEmpty().isNotEmpty())
            walk(n.id, if (n.depth == 0) emptyList() else cont + !last)
        }
    }
    walk(null, emptyList())
    return out
}

private val START = 16.dp
private val STEP = 22.dp
private val DOT_CENTER = 5.dp

/**
 * Строка дерева категорий: тонкие соединительные линии от группы к подпунктам
 * (по просьбе пользователя — как уголок «↳» в Money Manager, но непрерывной линией).
 */
@Composable
internal fun CategoryTreeRow(
    node: CategoryNode,
    guide: TreeGuide?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (guide == null) return@drawBehind
                val stroke = 1.dp.toPx()
                fun x(level: Int) = (START + STEP * level + DOT_CENTER).toPx()
                val cy = size.height / 2
                // От своей точки вниз — к первому подпункту, без разрыва внутри строки.
                if (guide.hasChildren) drawLine(lineColor, Offset(x(node.depth), cy + DOT_CENTER.toPx()), Offset(x(node.depth), size.height), stroke)
                if (node.depth == 0) return@drawBehind
                guide.ancestorsContinue.forEachIndexed { level, continues ->
                    if (continues) drawLine(lineColor, Offset(x(level), 0f), Offset(x(level), size.height), stroke)
                }
                val elbow = x(node.depth - 1)
                drawLine(lineColor, Offset(elbow, 0f), Offset(elbow, if (guide.isLast) cy else size.height), stroke)
                drawLine(lineColor, Offset(elbow, cy), Offset((START + STEP * node.depth).toPx() - 3.dp.toPx(), cy), stroke)
            }
            .padding(start = START + STEP * node.depth, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(node.color)
        Spacer(Modifier.width(12.dp))
        Text(
            node.name,
            modifier = Modifier.weight(1f),
            style = if (node.depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
        trailing()
    }
}
