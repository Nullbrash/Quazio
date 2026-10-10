package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.nullbrash.quazio.core.ui.LocalReminderPlatform
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.bg_restricted
import io.github.nullbrash.quazio.core.ui.res.bg_restricted_open
import org.jetbrains.compose.resources.stringResource

/**
 * «Телефон ограничил Quazio в фоне» — в «Напоминаниях» и «Виджете»: с ограничением оба молча
 * не работают, пока приложение закрыто. Проверка — при каждом возврате на экран (из настроек).
 */
@Composable
internal fun BackgroundWarning() {
    val platform = LocalReminderPlatform.current ?: return
    var restricted by remember { mutableStateOf(platform.backgroundRestricted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = platform.backgroundRestricted() }
    if (!restricted) return
    Column {
        Text(stringResource(Res.string.bg_restricted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { platform.openBackgroundSettings() }) { Text(stringResource(Res.string.bg_restricted_open)) }
    }
}
