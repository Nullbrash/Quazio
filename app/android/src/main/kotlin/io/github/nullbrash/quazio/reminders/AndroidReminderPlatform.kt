package io.github.nullbrash.quazio.reminders

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.nullbrash.quazio.core.ui.ReminderPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlin.concurrent.thread

/**
 * Напоминания на телефоне: разрешения (уведомления — при включении напоминаний; будильник на
 * весь экран — отдельное на Android 14), выбор мелодии телефона. Создавать в onCreate.
 */
internal class AndroidReminderPlatform(private val activity: ComponentActivity) : ReminderPlatform {

    override val phone = true

    private var permission: CompletableDeferred<Boolean>? = null
    private val askPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permission?.complete(ok)
        permission = null
    }

    private var sound: CompletableDeferred<String?>? = null
    private val pickRingtone = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri: Uri? = result.data?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        sound?.complete(if (result.resultCode == android.app.Activity.RESULT_OK) uri?.toString().orEmpty() else null)
        sound = null
    }

    override fun reschedule() {
        val context = activity.applicationContext
        thread(name = "quazio-reminders") { ReminderEngine.dispatch(context) }
    }

    override suspend fun ensureNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true
        val result = CompletableDeferred<Boolean>()
        permission = result
        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        return result.await()
    }

    override fun canFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || activity.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    override fun openFullScreenSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${activity.packageName}")))
        }
    }

    override suspend fun pickSound(current: String?, alarm: Boolean): String? {
        val result = CompletableDeferred<String?>()
        sound = result
        pickRingtone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, if (alarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current?.let(Uri::parse)))
        return result.await()
    }

    override fun soundName(uri: String?, alarm: Boolean): String? =
        uri?.let { runCatching { RingtoneManager.getRingtone(activity, Uri.parse(it))?.getTitle(activity) }.getOrNull() }

    override fun backgroundRestricted(): Boolean =
        Build.VERSION.SDK_INT >= 28 && activity.getSystemService(ActivityManager::class.java).isBackgroundRestricted

    // Прямого перехода на страницу батареи приложения в Android нет — открываем «О приложении».
    override fun openBackgroundSettings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }
}
