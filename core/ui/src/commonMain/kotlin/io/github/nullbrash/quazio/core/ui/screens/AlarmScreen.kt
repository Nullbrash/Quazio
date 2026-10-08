package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.rem_dismiss
import io.github.nullbrash.quazio.core.ui.res.rem_snooze_action
import org.jetbrains.compose.resources.stringResource

/** Экран будильника: «Отключить» / «Отложить». Подробный будильник — этап 7 (по образцу AMdroid). */
@Composable
fun AlarmScreen(title: String, body: String, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.rem_dismiss)) }
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.rem_snooze_action)) }
            }
        }
    }
}
