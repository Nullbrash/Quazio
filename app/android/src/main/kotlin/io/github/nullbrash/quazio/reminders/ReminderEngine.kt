package io.github.nullbrash.quazio.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import io.github.nullbrash.quazio.AppGraph
import io.github.nullbrash.quazio.MainActivity
import io.github.nullbrash.quazio.core.ui.nowNextText
import io.github.nullbrash.quazio.core.ui.reminderText
import io.github.nullbrash.quazio.feature.reminders.Reminder
import io.github.nullbrash.quazio.feature.reminders.ReminderKind
import io.github.nullbrash.quazio.feature.reminders.ReminderProfile
import io.github.nullbrash.quazio.feature.reminders.ReminderService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.github.nullbrash.quazio.core.ui.ReminderLabels
import kotlin.time.Instant

/**
 * Напоминания на телефоне: один точный будильник системы на ближайшее напоминание; когда
 * он срабатывает — показать наступившее и поставить следующий. Постоянной службы нет.
 * Вызывать не из главного потока (база, календари).
 */
internal object ReminderEngine {

    fun reminders(context: Context): ReminderService? = AppGraph.services(context).reminders

    /** Пересчитать, когда проснуться: ближайшее напоминание или смена «сейчас / дальше». */
    fun reschedule(context: Context) {
        val r = reminders(context) ?: return
        val now = System.currentTimeMillis()
        val next = listOfNotNull(r.nextAt(now), if (r.settings.persistentEnabled) r.nextBoundary(now) else null).min()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Точный будильник — только с разрешением (Android 12: SCHEDULE_EXACT_ALARM, 13+: USE_EXACT_ALARM).
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    /** Показать наступившее, обновить постоянное уведомление, поставить следующий будильник. */
    fun dispatch(context: Context) {
        val r = reminders(context) ?: return
        val now = System.currentTimeMillis()
        val due = r.due(now)
        due.forEach { show(context, r, it) }
        r.markShown(due, now)
        updatePersistent(context, r, now)
        reschedule(context)
    }

    fun show(context: Context, r: ReminderService, reminder: Reminder) {
        val profile = r.profiles.byId(reminder.profileId)
        val (title, body) = runBlocking { reminderText(reminder, r) }
        if (profile.kind == ReminderKind.ALARM) {
            // Текст — сразу службе: отложенное после показа из списка отложенных убирается.
            AlarmService.start(context, reminder.key, title, body, profile.id)
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        val b = Notification.Builder(context, channelFor(context, profile))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            // На экране блокировки — без подробностей.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(context, channelFor(context, profile)).setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Quazio").build())
            .setContentIntent(openIntent(context, reminder))
        actions(context, reminder, profile).forEach(b::addAction)
        nm.notify(notificationId(reminder.key), b.build())
    }

    /** «Записать» (у «спрашивать» в день платежа), «Открыть» и «Отложить». */
    fun actions(context: Context, reminder: Reminder, profile: ReminderProfile): List<Notification.Action> = buildList {
        if (reminder is Reminder.Payment && reminder.ask && reminder.daysBefore == 0) {
            val record = Notification.Action.Builder(null, runBlocking { ReminderLabels.record() },
                ReminderReceiver.action(context, ReminderReceiver.RECORD, reminder.key))
            // «Записать» с экрана блокировки — только после разблокировки (это запись в финансы).
            if (Build.VERSION.SDK_INT >= 31) record.setAuthenticationRequired(true)
            add(record.build())
            add(Notification.Action.Builder(null, runBlocking { ReminderLabels.open() }, openIntent(context, reminder)).build())
        }
        add(Notification.Action.Builder(null, runBlocking { ReminderLabels.snooze() },
            ReminderReceiver.action(context, ReminderReceiver.SNOOZE, reminder.key)).build())
    }

    fun openIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (reminder) {
            is Reminder.Payment -> intent.putExtra(MainActivity.EXTRA_OPEN_RECORD, "${reminder.recurringId}|${reminder.date}")
            is Reminder.Summary -> intent.putExtra(MainActivity.EXTRA_OPEN_DAY, reminder.date.toString())
            is Reminder.EventEnd -> intent.putExtra(MainActivity.EXTRA_OPEN_DAY,
                Instant.fromEpochMilliseconds(reminder.end).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
        }
        return PendingIntent.getActivity(context, notificationId(reminder.key), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** У платежа — одно уведомление на повторение: «в день» заменяет «накануне». */
    fun notificationId(key: String): Int {
        val k = key.substringBefore('#')
        return (if (k.startsWith("pay|")) k.split('|').take(3).joinToString("|") else k).hashCode()
    }

    /** «Сейчас / дальше» в шторке — по настройке (по умолчанию выключено). */
    fun updatePersistent(context: Context, r: ReminderService, now: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!r.settings.persistentEnabled) {
            nm.cancel(PERSISTENT_ID)
            return
        }
        ensureChannel(context, CHANNEL_NOW, runBlocking { ReminderLabels.channelNow() }, NotificationManager.IMPORTANCE_LOW, null, false)
        val (title, body) = runBlocking { nowNextText(r.nowNext(now)) }
        nm.notify(PERSISTENT_ID, Notification.Builder(context, CHANNEL_NOW)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(context, PERSISTENT_ID, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build())
    }

    /**
     * Канал уведомлений под профиль: звук и вибрацию канала после создания поменять нельзя,
     * поэтому у каждого сочетания — свой канал (старые удаляются).
     */
    fun channelFor(context: Context, p: ReminderProfile): String {
        val sound = p.soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val id = "rem_${p.id}_${(sound.toString() + p.vibrate).hashCode().toUInt().toString(16)}"
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notificationChannels.filter { it.id.startsWith("rem_${p.id}_") && it.id != id }.forEach { nm.deleteNotificationChannel(it.id) }
        val name = if (p.builtin) runBlocking { ReminderLabels.channelReminders() } else p.name
        ensureChannel(context, id, name, NotificationManager.IMPORTANCE_HIGH, sound, p.vibrate)
        return id
    }

    fun alarmChannel(context: Context): String {
        // Звук будильника играет служба (нарастание громкости), у канала — без звука.
        ensureChannel(context, CHANNEL_ALARM, runBlocking { ReminderLabels.channelAlarm() }, NotificationManager.IMPORTANCE_HIGH, null, false, silent = true)
        return CHANNEL_ALARM
    }

    private fun ensureChannel(context: Context, id: String, name: String, importance: Int, sound: Uri?, vibrate: Boolean, silent: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(NotificationChannel(id, name, importance).apply {
            enableVibration(vibrate)
            if (silent) setSound(null, null)
            else if (sound != null) setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        })
    }

    const val PERSISTENT_ID = 7001
    private const val CHANNEL_NOW = "now_next"
    private const val CHANNEL_ALARM = "alarm"
}
