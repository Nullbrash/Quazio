package io.github.nullbrash.quazio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.asImageBitmap
import androidx.fragment.app.FragmentActivity
import io.github.nullbrash.quazio.core.ui.IncomingText
import io.github.nullbrash.quazio.core.ui.QuazioApp
import io.github.nullbrash.quazio.core.ui.OpenRequest
import io.github.nullbrash.quazio.core.ui.OpenRequests
import io.github.nullbrash.quazio.reminders.AndroidReminderPlatform
import io.github.nullbrash.quazio.widget.DialWidgets
import kotlinx.datetime.LocalDate

// FragmentActivity, а не ComponentActivity: системному окну входа (BiometricPrompt) нужны фрагменты.
class MainActivity : FragmentActivity() {

    private val incoming = IncomingText()
    private val openRequests = OpenRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deviceAuth = AndroidDeviceAuthenticator(this)
        val fileSaver = AndroidFileSaver(this)
        val calendarAccess = AndroidCalendarAccess(this)
        val reminderPlatform = AndroidReminderPlatform(this)
        // После поворота экрана intent тот же — текст уже разобран, второй раз не нужен.
        if (savedInstanceState == null) {
            incoming.offer(intent.getStringExtra(EXTRA_QUICK_TEXT))
            openRequests.offer(openRequestOf(intent))
        }
        setContent {
            QuazioApp(
                versionName = BuildConfig.VERSION_NAME,
                openServices = { AppGraph.services(applicationContext) },
                deviceAuth = deviceAuth,
                fileSaver = fileSaver,
                incoming = incoming,
                calendarAccess = calendarAccess,
                reminderPlatform = reminderPlatform,
                openRequests = openRequests,
                widgetUpdater = { DialWidgets.updateAsync(applicationContext) },
                widgetPreview = { look, side -> DialWidgets.preview(applicationContext, look, side)?.asImageBitmap() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.offer(intent.getStringExtra(EXTRA_QUICK_TEXT))
        openRequests.offer(openRequestOf(intent))
    }

    /** Нажатие на уведомление: «Открыть» платёж — окно «Записать», сводка и конец события — день. */
    private fun openRequestOf(intent: Intent): OpenRequest? {
        intent.getStringExtra(EXTRA_OPEN_RECORD)?.let { v ->
            val date = runCatching { LocalDate.parse(v.substringAfter('|')) }.getOrNull() ?: return null
            return OpenRequest.RecordPayment(v.substringBefore('|'), date)
        }
        if (intent.getBooleanExtra(EXTRA_NEW_EVENT, false)) return OpenRequest.NewEvent
        return intent.getStringExtra(EXTRA_OPEN_DAY)?.let { v -> runCatching { OpenRequest.CalendarDay(LocalDate.parse(v)) }.getOrNull() }
    }

    companion object {
        const val EXTRA_OPEN_RECORD = "io.github.nullbrash.quazio.OPEN_RECORD"
        const val EXTRA_OPEN_DAY = "io.github.nullbrash.quazio.OPEN_DAY"
        const val EXTRA_NEW_EVENT = "io.github.nullbrash.quazio.NEW_EVENT"

        /** Текст из «В учёт Quazio» / «Поделиться» — передаёт [QuickTextActivity]. */
        const val EXTRA_QUICK_TEXT = "io.github.nullbrash.quazio.QUICK_TEXT"
    }
}
