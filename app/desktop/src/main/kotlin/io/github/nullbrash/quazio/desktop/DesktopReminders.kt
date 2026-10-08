package io.github.nullbrash.quazio.desktop

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import io.github.nullbrash.quazio.core.ui.ReminderPlatform
import io.github.nullbrash.quazio.core.ui.reminderText
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Напоминания на ПК — уведомления Windows, пока окно Quazio открыто (решение пользователя;
 * в трей не сворачивается). Будильника на весь экран на ПК нет: любой профиль — уведомление.
 */
internal class DesktopReminders : ReminderPlatform {
    override val phone = false

    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun reschedule() {
        wake.trySend(Unit)
    }

    /** Раз в полминуты (и сразу после изменений) показать наступившие напоминания. */
    suspend fun run(reminders: () -> ReminderService, tray: TrayState) {
        while (true) {
            val due = withContext(Dispatchers.IO) {
                val r = reminders()
                if (!r.settings.desktopEnabled) return@withContext emptyList()
                val now = System.currentTimeMillis()
                r.due(now).also { r.markShown(it, now) }.map { reminderText(it, r) }
            }
            due.forEach { (title, body) -> tray.sendNotification(Notification(title, body, Notification.Type.Info)) }
            withTimeoutOrNull(CHECK_MS) { wake.receive() }
        }
    }

    private companion object {
        const val CHECK_MS = 30_000L
    }
}
