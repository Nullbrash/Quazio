package io.github.nullbrash.quazio.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.nullbrash.quazio.core.ui.screens.AlarmScreen

/** Экран будильника поверх блокировки: «Отключить» / «Отложить». Звук — в [AlarmService]. */
class AlarmActivity : ComponentActivity() {

    private val close = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Поверх блокировки и с включением экрана: на Android 8 — только флагами окна.
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(close, IntentFilter(CLOSE), RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(close, IntentFilter(CLOSE))
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        setContent { AlarmScreen(title, body, onDismiss = { send(AlarmService.DISMISS) }, onSnooze = { send(AlarmService.SNOOZE) }) }
    }

    private fun send(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(close) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val CLOSE = "io.github.nullbrash.quazio.ALARM_CLOSE"
    }
}
