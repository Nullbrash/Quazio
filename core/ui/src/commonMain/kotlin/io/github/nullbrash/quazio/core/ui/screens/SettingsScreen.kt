package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.DeviceAuthenticator
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.app_name
import io.github.nullbrash.quazio.core.ui.res.settings_about
import io.github.nullbrash.quazio.core.ui.res.settings_license
import io.github.nullbrash.quazio.core.ui.res.settings_version
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(versionName: String, services: AppServices, deviceAuth: DeviceAuthenticator?) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AccountsSection(services)
        HorizontalDivider()
        SecuritySection(services, deviceAuth)
        HorizontalDivider()
        FinanceSettingsSection(services)
        HorizontalDivider()
        if (services.calendar != null && services.calendarPrefs != null) {
            CalendarSettingsSection(services.calendar, services.calendarPrefs)
            HorizontalDivider()
        }
        if (services.reminders != null) {
            RemindersSection(services)
            HorizontalDivider()
        }
        if (services.widgetPrefs != null && io.github.nullbrash.quazio.core.ui.LocalWidgetUpdater.current != null) {
            WidgetSection(services.widgetPrefs)
            HorizontalDivider()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.settings_about), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.settings_version, versionName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.settings_license), style = MaterialTheme.typography.bodySmall)
        }
    }
}
