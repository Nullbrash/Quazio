package io.github.nullbrash.quazio.reminders

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.nullbrash.quazio.feature.reminders.ReminderProfile
import kotlinx.coroutines.runBlocking
import io.github.nullbrash.quazio.core.ui.ReminderLabels
import kotlin.concurrent.thread

/**
 * Будильник: звонит, пока не отключат или не отложат (не дольше [MAX_RING_MS]). Звук —
 * по громкости будильника телефона, с нарастанием из профиля; экран — [AlarmActivity].
 * Подробная логика будильников — этап 7 (по образцу AMdroid, решение пользователя).
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var key: String? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            DISMISS -> stopRinging()
            SNOOZE -> {
                val k = key ?: intent.getStringExtra(EXTRA_KEY)
                thread {
                    val r = ReminderEngine.reminders(applicationContext)
                    val now = System.currentTimeMillis()
                    val reminder = k?.let { r?.find(it, now) }
                    if (r != null && reminder != null) r.snooze(reminder, r.profiles.byId(reminder.profileId).snoozeMinutes, now)
                    ReminderEngine.reschedule(applicationContext)
                }
                stopRinging()
            }
            else -> {
                val k = intent?.getStringExtra(EXTRA_KEY) ?: return START_NOT_STICKY
                start(k, intent.getStringExtra(EXTRA_TITLE) ?: "Quazio", intent.getStringExtra(EXTRA_BODY).orEmpty(),
                    intent.getStringExtra(EXTRA_PROFILE) ?: "alarm")
            }
        }
        return START_NOT_STICKY
    }

    private fun start(reminderKey: String, title: String, body: String, profileId: String) {
        key = reminderKey
        // Экран — включить, но не раньше, чем система откроет экран будильника по уведомлению:
        // при уже включённом экране она показывает только верхнее уведомление. Одного turnScreenOn
        // мало — на эмуляторе экран то включался, то оставался погашенным.
        handler.postDelayed({
            if (key == null) return@postDelayed
            @Suppress("DEPRECATION")
            wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "quazio:alarm")
                .apply { acquire(MAX_RING_MS) }
        }, WAKE_DELAY_MS)
        // Сразу — на передний план (Android требует в течение секунд); звук — следом (профиль из базы).
        val first = build(title, body, reminderKey)
        // Вид службы указывается с Android 10; на 8–9 такого вызова нет.
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, first)
        thread {
            val profile = ReminderEngine.reminders(applicationContext)?.profiles?.byId(profileId) ?: return@thread
            handler.post { ring(profile) }
        }
        handler.postDelayed({ stopRinging() }, MAX_RING_MS)
    }

    private fun build(title: String, body: String, reminderKey: String): Notification {
        val screen = PendingIntent.getActivity(this, 1,
            Intent(this, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_TITLE, title).putExtra(AlarmActivity.EXTRA_BODY, body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, ReminderEngine.alarmChannel(this))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .addAction(Notification.Action.Builder(null, runBlocking { ReminderLabels.dismiss() }, command(this, DISMISS, reminderKey)).build())
            .addAction(Notification.Action.Builder(null, runBlocking { ReminderLabels.snooze() }, command(this, SNOOZE, reminderKey)).build())
            .build()
    }

    private fun ring(profile: ReminderProfile) {
        val uri = profile.soundUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        // Нарастание громкости: ступенями до полной за rampSeconds.
        val ramp = profile.rampSeconds
        if (ramp > 0) {
            val steps = ramp * 2
            for (i in 0..steps) handler.postDelayed({ val v = i.toFloat() / steps; player?.setVolume(v, v) }, i * 500L)
        }
        if (profile.vibrate) {
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        }
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        key = null
        getSystemService(Vibrator::class.java)?.cancel()
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        sendBroadcast(Intent(AlarmActivity.CLOSE).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        super.onDestroy()
    }

    companion object {
        const val DISMISS = "io.github.nullbrash.quazio.ALARM_DISMISS"
        const val SNOOZE = "io.github.nullbrash.quazio.ALARM_SNOOZE"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_PROFILE = "profile"
        private const val NOTIFICATION_ID = 7002
        private const val MAX_RING_MS = 10 * 60_000L
        private const val WAKE_DELAY_MS = 1_500L

        fun start(context: Context, key: String, title: String, body: String, profileId: String) {
            context.startForegroundService(Intent(context, AlarmService::class.java).putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body).putExtra(EXTRA_PROFILE, profileId))
        }

        fun command(context: Context, action: String, key: String?): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, AlarmService::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
