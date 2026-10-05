package io.github.nullbrash.quazio.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Значки, которых нет в малом наборе `material-icons-core`. Контуры — из
 * Material Icons (Google, Apache-2.0): так не тянем библиотеку всех значков.
 */
internal object QuazioIcons {

    /** `account_balance_wallet`, вариант Filled. */
    val Wallet: ImageVector by lazy {
        icon(
            "Wallet",
            "M21 18v1c0 1.1-.9 2-2 2H5c-1.11 0-2-.9-2-2V5c0-1.1.89-2 2-2h14c1.1 0 2 .9 2 2v1h-9c-1.11 0-2 .9-2 2v8" +
                "c0 1.1.89 2 2 2h9zm-9-2h10V8H12v8zm4-2.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5" +
                "-.67 1.5-1.5 1.5z",
        )
    }

    // Цвет заливки неважен: Icon перекрашивает значок в цвет текста.
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
