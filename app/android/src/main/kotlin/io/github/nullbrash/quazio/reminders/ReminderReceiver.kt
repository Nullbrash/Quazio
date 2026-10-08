package io.github.nullbrash.quazio.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nullbrash.quazio.AppGraph
import io.github.nullbrash.quazio.feature.reminders.Reminder
import kotlinx.datetime.TimeZone
import kotlin.concurrent.thread

/**
 * Будильник напоминаний сработал, телефон перезагрузили, сменили время — показать
 * наступившее и поставить следующий будильник. Кнопки уведомления: «Записать», «Отложить».
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        // База и календари — не в главном потоке.
        thread(name = "quazio-reminders") {
            try {
                when (intent.action) {
                    RECORD -> record(app, intent.getStringExtra(EXTRA_KEY))
                    SNOOZE -> snooze(app, intent.getStringExtra(EXTRA_KEY))
                }
                ReminderEngine.dispatch(app)
            } finally {
                pending.finish()
            }
        }
    }

    /** «Записать» прямо из уведомления — с суммой платежа (решение пользователя). */
    private fun record(context: Context, key: String?) {
        key ?: return
        val services = AppGraph.services(context)
        val r = services.reminders ?: return
        val payment = r.find(key, System.currentTimeMillis()) as? Reminder.Payment ?: return
        val rec = services.finance.recurring
        val plan = rec.byId(payment.recurringId) ?: return
        rec.record(services.accounts.current().id, plan.id, payment.date, rec.defaultTxn(plan, payment.date, TimeZone.currentSystemDefault().id))
        context.getSystemService(NotificationManager::class.java).cancel(ReminderEngine.notificationId(key))
    }

    private fun snooze(context: Context, key: String?) {
        key ?: return
        val r = AppGraph.services(context).reminders ?: return
        val now = System.currentTimeMillis()
        val reminder = r.find(key, now) ?: return
        r.snooze(reminder, r.profiles.byId(reminder.profileId).snoozeMinutes, now)
        context.getSystemService(NotificationManager::class.java).cancel(ReminderEngine.notificationId(key))
    }

    companion object {
        const val TICK = "io.github.nullbrash.quazio.REMINDER_TICK"
        const val RECORD = "io.github.nullbrash.quazio.REMINDER_RECORD"
        const val SNOOZE = "io.github.nullbrash.quazio.REMINDER_SNOOZE"
        private const val EXTRA_KEY = "key"

        fun action(context: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
            context, (action + key).hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
