package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.finance_placeholder
import org.jetbrains.compose.resources.stringResource

/** Заглушка раздела финансов — наполняется в фазе 4 этапа 1. */
@Composable
fun FinanceScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(Res.string.finance_placeholder), style = MaterialTheme.typography.bodyLarge)
    }
}
