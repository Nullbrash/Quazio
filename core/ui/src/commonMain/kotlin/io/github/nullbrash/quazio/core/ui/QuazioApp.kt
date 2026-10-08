package io.github.nullbrash.quazio.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.accounts_default_name
import io.github.nullbrash.quazio.core.ui.res.error_open_data
import io.github.nullbrash.quazio.core.ui.res.loading
import io.github.nullbrash.quazio.core.ui.res.nav_calendar
import io.github.nullbrash.quazio.core.ui.res.nav_finance
import io.github.nullbrash.quazio.core.ui.res.nav_settings
import io.github.nullbrash.quazio.core.ui.screens.calendar.CalendarCache
import io.github.nullbrash.quazio.core.ui.screens.calendar.CalendarScreen
import io.github.nullbrash.quazio.core.ui.screens.finance.FinanceScreen
import io.github.nullbrash.quazio.feature.calendar.ical.LinkedCalendars
import kotlinx.datetime.LocalDate
import androidx.compose.runtime.remember
import io.github.nullbrash.quazio.core.ui.screens.LockGate
import io.github.nullbrash.quazio.core.ui.screens.SettingsScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private sealed interface Startup {
    data object Loading : Startup
    data class Ready(val services: AppServices) : Startup
    data class Failed(val message: String) : Startup
}

/**
 * Общий корень Android и ПК: тема, навигация, разделы.
 * [openServices] открывает базу — вызывается в фоне, интерфейс тем временем уже рисуется.
 */
@Composable
fun QuazioApp(
    versionName: String,
    openServices: () -> AppServices,
    deviceAuth: DeviceAuthenticator? = null,
    fileSaver: FileSaver? = null,
    incoming: IncomingText? = null,
    calendarAccess: CalendarAccess? = null,
) = CompositionLocalProvider(
    LocalFileSaver provides fileSaver,
    LocalIncomingText provides incoming,
    LocalCalendarAccess provides calendarAccess,
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        val startup by produceState<Startup>(Startup.Loading) {
            value = try {
                val defaultName = getString(Res.string.accounts_default_name)
                val services = withContext(Dispatchers.IO) {
                    openServices().also {
                        it.accounts.initialize(it.deviceName, it.platform, defaultName)
                        it.lock.setTimeout(it.vault.timeout)
                    }
                }
                Startup.Ready(services)
            } catch (e: Exception) {
                Startup.Failed(e.message ?: e.toString())
            }
        }
        when (val s = startup) {
            Startup.Loading -> CenteredText(stringResource(Res.string.loading))
            is Startup.Failed -> CenteredText(stringResource(Res.string.error_open_data, s.message))
            is Startup.Ready -> Shell(versionName, s.services, deviceAuth)
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Surface(Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) { Text(text, modifier = Modifier.padding(24.dp)) }
    }
}

@Composable
private fun Shell(versionName: String, services: AppServices, deviceAuth: DeviceAuthenticator?) {
    var current by rememberSaveable { mutableStateOf(Destination.FINANCE) }
    // Нажатие на месяц в финансах — этот месяц в календаре (пожелание пользователя).
    var calendarJump by remember { mutableStateOf<LocalDate?>(null) }
    // События календаря помнятся между переключениями разделов — вкладка открывается без пустого кадра.
    val calendarCache = remember { CalendarCache() }
    val destinations = Destination.entries.filter { it != Destination.CALENDAR || services.calendar != null }
    val openCalendar: (LocalDate) -> Unit = { calendarJump = it; current = Destination.CALENDAR }
    // Прислали текст «В учёт Quazio» — его разбирают финансы.
    val incomingText = LocalIncomingText.current?.text?.collectAsState()?.value
    LaunchedEffect(incomingText) { if (incomingText != null) current = Destination.FINANCE }
    // «Назад» из других разделов — в «Финансы»; из «Финансов» — выход, как обычно на Android.
    SystemBack(enabled = current != Destination.FINANCE) { current = Destination.FINANCE }

    // Уход в фон и возвращение — для повторного входа (одинаково на телефоне и ПК).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { services.lock.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { services.lock.onForeground() }

    // ПК: календари по ссылкам — загрузка раз в 15 минут, пока окно открыто (решение пользователя).
    val refresher = remember(services) {
        val links = services.calendar as? LinkedCalendars
        val prefs = services.calendarPrefs
        if (links != null && prefs != null) CalendarRefresher(links, prefs) else null
    }
    LaunchedEffect(refresher) { refresher?.runWhileOpen() }

    CompositionLocalProvider(LocalCalendarRefresher provides refresher) { BoxWithConstraints(Modifier.fillMaxSize()) {
        when (navLayoutFor(maxWidth)) {
            NavLayout.BOTTOM_BAR -> Scaffold(
                bottomBar = {
                    NavigationBar {
                        destinations.forEach { d ->
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
                    DestinationContent(current, versionName, services, deviceAuth, calendarJump, { calendarJump = null }, openCalendar, calendarCache)
                }
            }

            NavLayout.SIDE_RAIL -> Scaffold { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    // Отступы от краёв экрана уже даёт Scaffold — у панели свои отключены.
                    NavigationRail(windowInsets = WindowInsets(0)) {
                        destinations.forEach { d ->
                            NavigationRailItem(
                                selected = d == current,
                                onClick = { current = d },
                                icon = { Icon(d.icon, contentDescription = null) },
                                label = { Text(stringResource(d.label)) },
                            )
                        }
                    }
                    Box(Modifier.fillMaxSize()) { DestinationContent(current, versionName, services, deviceAuth, calendarJump, { calendarJump = null }, openCalendar, calendarCache) }
                }
            }
        }
    } }
}

@Composable
private fun DestinationContent(
    destination: Destination,
    versionName: String,
    services: AppServices,
    deviceAuth: DeviceAuthenticator?,
    calendarJump: LocalDate?,
    onJumpHandled: () -> Unit,
    openCalendar: (LocalDate) -> Unit,
    calendarCache: CalendarCache,
) {
    val screen: @Composable () -> Unit = {
        when (destination) {
            Destination.FINANCE -> FinanceScreen(services, onOpenCalendar = if (services.calendar != null) openCalendar else null)
            Destination.CALENDAR -> {
                val source = services.calendar
                val prefs = services.calendarPrefs
                if (source != null && prefs != null) CalendarScreen(source, prefs, calendarJump, onJumpHandled, calendarCache)
            }
            Destination.SETTINGS -> SettingsScreen(versionName, services, deviceAuth)
        }
    }
    if (destination.isSensitive) LockGate(services, deviceAuth, screen) else screen()
}

private val Destination.label: StringResource
    get() = when (this) {
        Destination.FINANCE -> Res.string.nav_finance
        Destination.CALENDAR -> Res.string.nav_calendar
        Destination.SETTINGS -> Res.string.nav_settings
    }

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.FINANCE -> QuazioIcons.Wallet
        Destination.CALENDAR -> Icons.Filled.DateRange
        Destination.SETTINGS -> Icons.Filled.Settings
    }
