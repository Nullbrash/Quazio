package io.github.nullbrash.quazio.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.nullbrash.quazio.feature.calendar.DialLayout
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Циферблат виджета картинкой (виджеты Android не умеют рисовать дуги): как виджет
 * Sectograph пользователя — чёрный круг с тонкими часовыми линиями, белые цифры прямо на
 * обоях, цветные сектора событий, красная стрелка, белая дуга «осталось», оранжевое «завтра».
 * Геометрия — как у циферблата во вкладке «Календарь» (`DayDial`).
 */
internal object DialBitmap {

    fun render(
        layout: DialLayout,
        sizePx: Int,
        circleOpacity: Int,
        centerTop: String,
        centerBottom: String,
        untilText: String?,
        tomorrowText: String,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f * 0.80f
        val px = sizePx / 360f // единица размера: шрифты и линии растут с виджетом

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(circleOpacity * 255 / 100, 0, 0, 0) }
        cv.drawCircle(cx, cy, radius, fill)

        val hours = if (layout.labels.size > 12) 24 else 12
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255); strokeWidth = 1f * px.coerceAtLeast(1f) }
        for (i in 0 until hours) {
            val a = rad(i * 360f / hours)
            cv.drawLine(x(cx, radius * 0.30f, a), y(cy, radius * 0.30f, a), x(cx, radius, a), y(cy, radius, a), tick)
        }

        // Сектора событий: «дорожка» 0 — внешняя.
        val bandOuter = radius * 0.97f
        val bandInner = radius * 0.40f
        val laneWidth = (bandOuter - bandInner) / layout.lanes
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * px * 1.25f }
        for (s in layout.sectors) {
            val mid = bandOuter - laneWidth * (s.lane + 0.5f)
            arc.color = (s.event.color ?: 0xFF9E9E9E).toInt()
            arc.strokeWidth = laneWidth * 0.92f
            cv.drawArc(RectF(cx - mid, cy - mid, cx + mid, cy + mid), s.startAngle - 90f, s.sweep.coerceAtLeast(1f), false, arc)
            if (s.sweep >= 14f && s.event.title.isNotBlank()) {
                val a = rad(s.startAngle + s.sweep / 2)
                text(cv, s.event.title, x(cx, mid, a), y(cy, mid, a), titlePaint, maxWidth = laneWidth * 1.6f)
            }
        }

        // «Осталось» до следующего события — белая дуга внутри, подпись у её конца.
        layout.untilNext?.let { u ->
            val r = radius * 0.33f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = radius * 0.04f; strokeCap = Paint.Cap.ROUND }
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), u.fromAngle - 90f, u.sweep.coerceAtLeast(1f), false, p)
            untilText?.let {
                val a = rad(u.fromAngle + u.sweep + 12f)
                text(cv, it, x(cx, r * 1.25f, a), y(cy, r * 1.25f, a), TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11f * px * 1.25f })
            }
        }

        // Граница завтрашнего дня.
        layout.tomorrowAngle?.let { a ->
            val r = radius * 0.27f
            val orange = Color.rgb(0xFF, 0x98, 0x00)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = orange; strokeWidth = radius * 0.03f }
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), a - 90f - 20f, 40f, false, p)
            val ar = rad(a)
            text(cv, tomorrowText, x(cx, r * 1.35f, ar), y(cy, r * 1.35f, ar),
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = orange; textSize = 9f * px * 1.25f; typeface = Typeface.DEFAULT_BOLD })
        }

        // Цифры часов — снаружи круга, белые прямо на обоях.
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textSize = (if (hours == 24) 9f else 13f) * px * 1.25f
            setShadowLayer(2f * px, 0f, 0f, Color.argb(160, 0, 0, 0)) // читается и на светлых обоях
        }
        for (l in layout.labels) {
            val a = rad(l.angle)
            text(cv, l.hour.toString(), x(cx, radius * 1.13f, a), y(cy, radius * 1.13f, a), label)
        }

        // Стрелка «сейчас».
        layout.nowAngle?.let { a ->
            val ar = rad(a)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xE5, 0x39, 0x35); strokeWidth = radius * 0.025f; strokeCap = Paint.Cap.ROUND }
            cv.drawLine(x(cx, radius * 0.30f, ar), y(cy, radius * 0.30f, ar), x(cx, radius * 1.03f, ar), y(cy, radius * 1.03f, ar), p)
        }

        text(cv, centerTop, cx, cy - radius * 0.06f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f * px * 1.25f; typeface = Typeface.DEFAULT_BOLD })
        text(cv, centerBottom, cx, cy + radius * 0.12f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f * px * 1.25f })
        return bmp
    }

    /** Угол циферблата (0 — сверху, по часовой) → радианы. */
    private fun rad(deg: Float) = (deg - 90.0) * PI / 180.0
    private fun x(c: Float, r: Float, a: Double) = c + r * cos(a).toFloat()
    private fun y(c: Float, r: Float, a: Double) = c + r * sin(a).toFloat()

    private fun text(cv: Canvas, s: String, cx: Float, cy: Float, p: TextPaint, maxWidth: Float = Float.MAX_VALUE) {
        val shown = if (maxWidth < Float.MAX_VALUE) TextUtils.ellipsize(s, p, maxWidth.coerceAtLeast(p.textSize * 2), TextUtils.TruncateAt.END).toString() else s
        val w = p.measureText(shown)
        val fm = p.fontMetrics
        cv.drawText(shown, cx - w / 2, cy - (fm.ascent + fm.descent) / 2, p)
    }
}
