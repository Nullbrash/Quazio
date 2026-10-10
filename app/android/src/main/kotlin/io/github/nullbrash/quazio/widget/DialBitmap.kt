package io.github.nullbrash.quazio.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.nullbrash.quazio.feature.calendar.DialColors
import io.github.nullbrash.quazio.feature.calendar.DialLayout
import io.github.nullbrash.quazio.feature.calendar.DialStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Циферблат виджета картинкой (виджеты Android не умеют рисовать дуги): как виджет
 * Sectograph пользователя — чёрный круг с тонкими часовыми линиями, белые цифры прямо на
 * обоях, цветные сектора событий, красная стрелка, белая дуга «осталось», оранжевое «завтра».
 * Геометрия — как у циферблата во вкладке «Календарь» (`DayDial`); цвета — из [DialStyle].
 */
internal object DialBitmap {

    fun render(
        layout: DialLayout,
        sizePx: Int,
        look: DialStyle,
        centerTop: String,
        centerBottom: String,
        untilText: String?,
        tomorrowText: String,
        distinguish: Boolean,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        // Круг — как можно крупнее (пожелание пользователя): по краю остаётся место только цифрам часов.
        val radius = sizePx / 2f * 0.85f
        val px = sizePx / 360f // единица размера: шрифты и линии растут с виджетом

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.background.toInt() }
        cv.drawCircle(cx, cy, radius, fill)

        val hours = if (layout.labels.size > 12) 24 else 12
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.hourTicks.toInt(); strokeWidth = 1f * px.coerceAtLeast(1f) }
        for (i in 0 until hours) {
            val a = rad(i * 360f / hours)
            cv.drawLine(x(cx, radius * 0.30f, a), y(cy, radius * 0.30f, a), x(cx, radius, a), y(cy, radius, a), tick)
        }

        // Сектора событий: «дорожка» 0 — внешняя; дорожки — только у пересекающихся (s.lanes).
        val bandOuter = radius * 0.97f
        val bandInner = radius * 0.40f
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val baseText = 12f * px * 1.25f
        val colors = DialColors.of(layout.sectors, distinguish, look.sectorBase)
        for ((i, s) in layout.sectors.withIndex()) {
            val laneWidth = (bandOuter - bandInner) / s.lanes
            val mid = bandOuter - laneWidth * (s.lane + 0.5f)
            arc.color = colors[i].toInt()
            arc.strokeWidth = laneWidth * 0.94f
            // Зазор между делами вплотную — иначе соседние сектора сливаются.
            val gap = if (s.sweep > 3f) 0.6f else 0f
            cv.drawArc(RectF(cx - mid, cy - mid, cx + mid, cy + mid), s.startAngle - 90f + gap, (s.sweep - 2 * gap).coerceAtLeast(1f), false, arc)
            if (s.event.title.isNotBlank()) sectorTitle(cv, s.event.title, look.eventText, cx, cy, mid, s.startAngle + s.sweep / 2, (s.sweep * PI / 180 * mid).toFloat(), laneWidth * 0.94f, baseText)
        }

        // «Осталось» до следующего события — белая дуга внутри, подпись у её конца.
        layout.untilNext?.let { u ->
            val r = radius * 0.33f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.waitingArc.toInt(); strokeWidth = radius * 0.04f; strokeCap = Paint.Cap.ROUND }
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), u.fromAngle - 90f, u.sweep.coerceAtLeast(1f), false, p)
            untilText?.let {
                // Внутри белой дуги: снаружи она наезжала на подписи секторов.
                val a = rad(u.fromAngle + u.sweep + 12f)
                text(cv, it, x(cx, r * 0.78f, a), y(cy, r * 0.78f, a), TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.waitingText.toInt(); textSize = 11f * px * 1.25f })
            }
        }

        // Граница завтрашнего дня.
        layout.tomorrowAngle?.let { a ->
            val r = radius * 0.27f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.dayArc.toInt(); strokeWidth = radius * 0.03f }
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), a - 90f - 20f, 40f, false, p)
            val ar = rad(a)
            text(cv, tomorrowText, x(cx, r * 1.35f, ar), y(cy, r * 1.35f, ar),
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.dayText.toInt(); textSize = 9f * px * 1.25f; typeface = Typeface.DEFAULT_BOLD })
        }

        // Цифры часов — снаружи круга, белые прямо на обоях.
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.hourNumbers.toInt(); typeface = Typeface.DEFAULT_BOLD; textSize = (if (hours == 24) 10f else 13f) * px * 1.25f
            setShadowLayer(2f * px, 0f, 0f, Color.argb(160, 0, 0, 0)) // читается и на светлых обоях
        }
        // Тень у текста рисуется и при прозрачном цвете — прозрачные цифры не рисуем совсем.
        if (visible(look.hourNumbers)) for (l in layout.labels) {
            val a = rad(l.angle)
            text(cv, l.hour.toString(), x(cx, radius * 1.10f, a), y(cy, radius * 1.10f, a), label)
        }

        // Стрелка «сейчас».
        layout.nowAngle?.let { a ->
            val ar = rad(a)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.hand.toInt(); strokeWidth = radius * 0.025f; strokeCap = Paint.Cap.ROUND }
            cv.drawLine(x(cx, radius * 0.30f, ar), y(cy, radius * 0.30f, ar), x(cx, radius * 1.03f, ar), y(cy, radius * 1.03f, ar), p)
        }

        text(cv, centerTop, cx, cy - radius * 0.06f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.centerText.toInt(); textSize = 22f * px * 1.25f; typeface = Typeface.DEFAULT_BOLD })
        text(cv, centerBottom, cx, cy + radius * 0.12f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.centerText.toInt(); textSize = 13f * px * 1.25f })
        return bmp
    }

    /**
     * Название в секторе: поперёк, если влезает; иначе — вдоль радиуса (в 24 часах час — узкий
     * клин, поперёк влезали две буквы: нашлось в перегруженном дне). Слева — не вверх ногами.
     */
    private fun sectorTitle(cv: Canvas, title: String, color: Long, cx: Float, cy: Float, mid: Float, angle: Float, arcLen: Float, thickness: Float, base: Float) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt(); textSize = base }
        val a = rad(angle)
        if (p.measureText(title) <= arcLen * 0.9f && p.textSize <= thickness * 0.55f) {
            text(cv, title, x(cx, mid, a), y(cy, mid, a), p)
            return
        }
        p.textSize = minOf(base, arcLen * 0.72f)
        if (p.textSize < base * 0.6f) return // сектор — полоска (15 минут в 24 часах): подпись не прочитать
        cv.save()
        cv.translate(x(cx, mid, a), y(cy, mid, a))
        cv.rotate(if (angle in 0f..180f) angle - 90f else angle + 90f)
        text(cv, title, 0f, 0f, p, maxWidth = thickness * 0.9f)
        cv.restore()
    }

    private fun visible(color: Long) = (color ushr 24) and 0xFF != 0L

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
