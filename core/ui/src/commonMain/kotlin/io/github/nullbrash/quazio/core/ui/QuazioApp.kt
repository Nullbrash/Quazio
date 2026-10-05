package io.github.nullbrash.quazio.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.nav_finance
import io.github.nullbrash.quazio.core.ui.res.nav_settings
import io.github.nullbrash.quazio.core.ui.screens.FinanceScreen
import io.github.nullbrash.quazio.core.ui.screens.SettingsScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Общий корень Android и ПК: тема, навигация, разделы. */
@Composable
fun QuazioApp(versionName: String) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        var current by rememberSaveable { mutableStateOf(Destination.FINANCE) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            when (navLayoutFor(maxWidth)) {
                NavLayout.BOTTOM_BAR -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Destination.entries.forEach { d ->
                                NavigationBarItem(
                                    selected = d == current,
                                    onClick = { current = d },
                                    icon = { Icon(d.icon, contentDescription = null) },
                                    label = { Text(stringResource(d.label)) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        DestinationContent(current, versionName)
                    }
                }

                NavLayout.SIDE_RAIL -> Scaffold { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        // Отступы от краёв экрана уже даёт Scaffold — у панели свои отключены.
                        NavigationRail(windowInsets = WindowInsets(0)) {
                            Destination.entries.forEach { d ->
                                NavigationRailItem(
                                    selected = d == current,
                                    onClick = { current = d },
                                    icon = { Icon(d.icon, contentDescription = null) },
                                    label = { Text(stringResource(d.label)) },
                                )
                            }
                        }
                        Box(Modifier.fillMaxSize()) { DestinationContent(current, versionName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationContent(destination: Destination, versionName: String) {
    when (destination) {
        Destination.FINANCE -> FinanceScreen()
        Destination.SETTINGS -> SettingsScreen(versionName)
    }
}

private val Destination.label: StringResource
    get() = when (this) {
        Destination.FINANCE -> Res.string.nav_finance
        Destination.SETTINGS -> Res.string.nav_settings
    }

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.FINANCE -> Icons.Filled.ShoppingCart
        Destination.SETTINGS -> Icons.Filled.Settings
    }
