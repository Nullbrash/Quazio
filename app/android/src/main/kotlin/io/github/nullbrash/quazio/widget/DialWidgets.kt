package io.github.nullbrash.quazio.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.RemoteViews
import io.github.nullbrash.quazio.AppGraph
import io.github.nullbrash.quazio.MainActivity
import io.github.nullbrash.quazio.R
import io.github.nullbrash.quazio.core.ui.WidgetLabels
import io.github.nullbrash.quazio.feature.calendar.AndroidCalendarSource
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.DialLayouts
import io.github.nullbrash.quazio.feature.calendar.DialMode
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.thread
import kotlin.time.Instant

/**
 * Виджет-циферблат: картинка круга + кнопки «календарь» и «+» (решение пользователя).
 * Обновляется раз в минуту будильником без пробуждения — пока телефон спит, не обновляется
 * и батарею не тратит; при изменении календарей — задачей системы по сигналу хранилища.
 */
internal object DialWidgets {

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, DialWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelTick(context)
            return
        }
        val services = AppGraph.services(context)
        val prefs = services.widgetPrefs ?: return
        val zone = TimeZone.currentSystemDefault()
        val now = System.currentTimeMillis()
        val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
        val dayStart = LocalDateTime(local.date, LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val dayEnd = LocalDateTime(local.date.plus(DatePeriod(days = 1)), LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val layout = DialLayouts.layout(
            if (prefs.dial24) DialMode.DAY_24 else DialMode.SLIDING_12,
            events(context, dayStart, dayEnd + DAY_MS), now, dayStart, dayEnd,
            minuteOfDay = { t -> Instant.fromEpochMilliseconds(t).toLocalDateTime(zone).let { it.hour * 60 + it.minute } },
        ).let { if (prefs.showUntilNext) it else it.copy(untilNext = null) }
        val center = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        val date = "${local.day}.${local.month.ordinal + 1}"
        val until = layout.untilNext?.let { "${it.minutes / 60}:${(it.minutes % 60).toString().padStart(2, '0')}" }
        val tomorrow = runBlocking { WidgetLabels.tomorrow() }
        val density = context.resources.displayMetrics.density
        for (id in ids) {
            val side = sideDp(mgr.getAppWidgetOptions(id))
            val px = (side * density).toInt().coerceIn(MIN_PX, MAX_PX)
            val views = RemoteViews(context.packageName, R.layout.widget_dial)
            views.setImageViewBitmap(R.id.widget_dial, DialBitmap.render(layout, px, prefs.circleOpacity, center, date, until, tomorrow))
            views.setOnClickPendingIntent(R.id.widget_dial, open(context, NEW_EVENT_NO))
            val buttons = if (prefs.showButtons) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.widget_calendar, buttons)
            views.setViewVisibility(R.id.widget_add, buttons)
            views.setOnClickPendingIntent(R.id.widget_calendar, open(context, NEW_EVENT_NO))
            views.setOnClickPendingIntent(R.id.widget_add, open(context, NEW_EVENT_YES))
            mgr.updateAppWidget(id, views)
        }
        scheduleTick(context, (now / MINUTE_MS + 1) * MINUTE_MS)
        CalendarChangeJob.schedule(context)
    }

    /** События показываемых календарей; календарь не включён или нет доступа — пустой круг (с часами). */
    private fun events(context: Context, from: Long, to: Long): List<CalendarEvent> {
        val services = AppGraph.services(context)
        val source = services.calendar ?: return emptyList()
        val prefs = services.calendarPrefs ?: return emptyList()
        if (!prefs.enabled || !AndroidCalendarSource.hasPermission(context)) return emptyList()
        return runCatching {
            val ids = prefs.shown(source.calendars()).mapTo(HashSet()) { it.id }
            source.events(from, to, ids)
        }.getOrDefault(emptyList())
    }

    /** Сторона квадрата, dp: в портрете — ширина × наибольшая высота виджета на рабочем столе. */
    private fun sideDp(options: Bundle): Int {
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_DP)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, DEFAULT_DP)
        return minOf(w, h).takeIf { it > 0 } ?: DEFAULT_DP
    }

    private fun open(context: Context, newEvent: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (newEvent == NEW_EVENT_YES) intent.putExtra(MainActivity.EXTRA_NEW_EVENT, true)
        else intent.putExtra(MainActivity.EXTRA_OPEN_DAY, Instant.fromEpochMilliseconds(System.currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
        return PendingIntent.getActivity(context, REQUEST_BASE + newEvent, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Без пробуждения (RTC, не RTC_WAKEUP): телефон спит — будильник ждёт, при включении экрана — сразу. */
    private fun scheduleTick(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = tickIntent(context)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, at, pi) else am.set(AlarmManager.RTC, at, pi)
    }

    private fun cancelTick(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(context, REQUEST_BASE,
        Intent(context, DialWidgetProvider::class.java).setAction(DialWidgetProvider.TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Обновить в фоне (база и календари — не в главном потоке). */
    fun updateAsync(context: Context, done: () -> Unit = {}) {
        val app = context.applicationContext
        thread(name = "quazio-widget") { try { updateAll(app) } finally { done() } }
    }

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 86_400_000L
    private const val DEFAULT_DP = 180
    private const val MIN_PX = 200
    private const val MAX_PX = 1200
    private const val REQUEST_BASE = 8100
    private const val NEW_EVENT_NO = 1
    private const val NEW_EVENT_YES = 2
}

class DialWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TICK) {
            val pending = goAsync()
            DialWidgets.updateAsync(context) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        DialWidgets.updateAsync(context) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Растянули на рабочем столе — перерисовать под новый размер.
        val pending = goAsync()
        DialWidgets.updateAsync(context) { pending.finish() }
    }

    override fun onDisabled(context: Context) {
        DialWidgets.updateAsync(context) // последний убран — будильник и задача снимутся
        CalendarChangeJob.cancel(context)
    }

    companion object {
        const val TICK = "io.github.nullbrash.quazio.WIDGET_TICK"
    }
}

/** Календари изменились (Google досинхронизировал, событие добавили в другом приложении). */
class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        DialWidgets.updateAsync(this) { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 8200

        /** Задача с сигналом хранилища — разовая: ставится заново после каждого срабатывания. */
        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000)
                .build()
            runCatching { js.schedule(job) }
        }

        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
