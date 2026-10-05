package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.calc_on_new
import io.github.nullbrash.quazio.core.ui.res.settings_finance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Настройки финансов этого устройства (та же настройка — и в окне операции). */
@Composable
fun FinanceSettingsSection(services: AppServices) {
    val scope = rememberCoroutineScope()
    var calcOnNew by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(services) { calcOnNew = withContext(Dispatchers.IO) { services.finance.openCalculatorOnNew } }
    val current = calcOnNew ?: return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.settings_finance), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.calc_on_new), modifier = Modifier.weight(1f))
            Switch(checked = current, onCheckedChange = { on ->
                calcOnNew = on
                scope.launch { withContext(Dispatchers.IO) { services.finance.openCalculatorOnNew = on } }
            })
        }
    }
}
