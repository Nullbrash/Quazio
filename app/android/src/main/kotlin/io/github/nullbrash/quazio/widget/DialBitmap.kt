package io.github.nullbrash.quazio.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import io.github.nullbrash.quazio.feature.calendar.DialColors
import io.github.nullbrash.quazio.feature.calendar.DialLayout
import io.github.nullbrash.quazio.feature.calendar.DialSector
import io.github.nullbrash.quazio.feature.calendar.DialStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Циферблат виджета картинкой (виджеты Android не умеют рисовать дуги): как виджет
 * Sectograph пользователя — чёрный круг с тонкими часовыми линиями, белые цифры прямо на
 * обоях, цветные сектора событий, красная стрелка, белая дуга «осталось», оранжевое «завтра».
 * Геометрия — как у циферблата во вкладке «Календарь» (`DayDial`); цвета — из [DialStyle].
 * Прозрачные элементы не рисуются: вид по умолчанию не зависит от новых настроек.
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
        hm: (Long) -> String = { "" },
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        // Круг — как можно крупнее (пожелание пользователя): по краю остаётся место только цифрам часов.
        val radius = sizePx / 2f * 0.85f
        val px = sizePx / 360f // единица размера: шрифты и линии растут с виджетом
        // Общая прозрачность — слоем на всё; при 100 % слоя нет, картинка та же, что без настройки.
        val layer = if (look.opacity < 100) cv.saveLayerAlpha(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), look.opacity * 255 / 100) else null

        // Фон: внешняя окружность (до края картинки, под цифрами часов), средняя — тонкий обод, основной.
        disc(cv, cx, cy, sizePx / 2f, look.outerRing)
        disc(cv, cx, cy, radius * 1.04f, look.middleRing)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.background.toInt() }
        cv.drawCircle(cx, cy, radius, fill)

        val hours = if (layout.labels.size > 12) 24 else 12
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.hourTicks.toInt(); strokeWidth = 1f * px.coerceAtLeast(1f) }
        for (i in 0 until hours) {
            val a = rad(i * 360f / hours)
            cv.drawLine(x(cx, radius * 0.30f, a), y(cy, radius * 0.30f, a), x(cx, radius, a), y(cy, radius, a), tick)
        }

        // Линия границы — где кончается показанное время.
        if (visible(look.timeBoundary)) {
            val a = rad(layout.boundaryAngle)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.timeBoundary.toInt(); strokeWidth = 1.5f * px.coerceAtLeast(1f) }
            cv.drawLine(x(cx, radius * 0.30f, a), y(cy, radius * 0.30f, a), x(cx, radius, a), y(cy, radius, a), p)
        }

        // Сектора событий: «дорожка» 0 — внешняя; дорожки — только у пересекающихся (s.lanes).
        val bandOuter = radius * 0.97f
        val bandInner = radius * 0.40f
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
        val baseText = 12f * px * 1.25f
        val colors = DialColors.of(layout.sectors, distinguish, look.sectorBase, look.allSectorsBase)
        for ((i, s) in layout.sectors.withIndex()) {
            val laneWidth = (bandOuter - bandInner) / s.lanes
            val mid = bandOuter - laneWidth * (s.lane + 0.5f)
            arc.color = colors[i].toInt()
            arc.strokeWidth = laneWidth * 0.94f
            // Зазор между делами вплотную — иначе соседние сектора сливаются.
            val gap = if (s.sweep > 3f) 0.6f else 0f
            val start = s.startAngle - 90f + gap
            val sweep = (s.sweep - 2 * gap).coerceAtLeast(1f)
            cv.drawArc(RectF(cx - mid, cy - mid, cx + mid, cy + mid), start, sweep, false, arc)
            // Время у внешнего края и подпись длительности у внутреннего забирают часть толщины —
            // название встаёт в середину оставшегося. Название важнее (решение пользователя): из
            // вариантов «время и длительность» → «время» → «длительность» → «ничего» берётся первый,
            // где название не обрезается сильнее, чем без них.
            val thick = laneWidth * 0.94f
            val small = baseText * 0.72f
            val strip = small * 1.6f
            val outer = mid + thick / 2
            val inner = mid - thick / 2
            val timeText = if (visible(look.sectorTime) || visible(look.sectorTimeBackground)) {
                val arcLen = (sweep * PI / 180 * (outer - strip / 2)).toFloat()
                val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = small }
                listOf("${hm(s.event.start)} – ${hm(s.event.end)}", hm(s.event.start)).firstOrNull { p.measureText(it) <= arcLen * 0.9f }
            } else null
            val arcLine = if (visible(look.durationArc)) small * 0.6f else 0f
            fun zone(time: Boolean, label: Boolean) = (outer - if (time) strip else 0f) to (inner + if (label) strip else arcLine)
            val plainShown = titleShown(s, zone(false, false), baseText)
            val (withTime, withLabel) = listOf(true to true, true to false, false to true, false to false)
                .filter { (t, l) -> (!t || timeText != null) && (!l || visible(look.durationArc)) }
                .first { (t, l) ->
                    val (zt, zb) = zone(t, l)
                    (!t && !l) || (zt - zb >= baseText * 1.3f && titleShown(s, zt to zb, baseText) >= plainShown)
                }
            val (top, bottom) = zone(withTime, withLabel)
            val timeShown = timeText.takeIf { withTime }
            val durationLabel = withLabel
            sectorDetails(cv, s, look, timeShown, durationLabel, cx, cy, outer, inner, start, sweep, small, strip, px)
            val titleMid = (top + bottom) / 2
            if (s.event.title.isNotBlank()) sectorTitle(cv, s.event.title, look.eventText, cx, cy, titleMid, s.startAngle + s.sweep / 2, (s.sweep * PI / 180 * titleMid).toFloat(), top - bottom, baseText)
        }

        // Центральная область — свой фон поверх внутренних концов часовых линий.
        disc(cv, cx, cy, radius * 0.38f, look.centerBackground)

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

        // Кольцо индикатора дня: фон — полное кольцо, поверх — граница завтрашнего дня.
        val dayR = radius * 0.27f
        if (visible(look.dayArcBackground)) {
            cv.drawCircle(cx, cy, dayR, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.dayArcBackground.toInt(); strokeWidth = radius * 0.03f })
        }
        layout.tomorrowAngle?.let { a ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.dayArc.toInt(); strokeWidth = radius * 0.03f }
            cv.drawArc(RectF(cx - dayR, cy - dayR, cx + dayR, cy + dayR), a - 90f - 20f, 40f, false, p)
            val ar = rad(a)
            text(cv, tomorrowText, x(cx, dayR * 1.35f, ar), y(cy, dayR * 1.35f, ar),
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
        layer?.let { cv.restoreToCount(it) }
        return bmp
    }

    /**
     * Время и длительность на секторе (как у образца): [time] («20:00 – 23:00» или только начало,
     * null — не поместилось) вдоль внешнего края на полоске фона; у внутреннего края — дуга
     * длительности и, если [durationLabel], «3:00» у её конца.
     */
    private fun sectorDetails(
        cv: Canvas, s: DialSector, look: DialStyle, time: String?, durationLabel: Boolean,
        cx: Float, cy: Float, outer: Float, inner: Float, start: Float, sweep: Float, size: Float, strip: Float, px: Float,
    ) {
        if (time != null) {
            val stripR = outer - strip / 2
            if (visible(look.sectorTimeBackground)) {
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.sectorTimeBackground.toInt(); strokeWidth = strip }
                cv.drawArc(RectF(cx - stripR, cy - stripR, cx + stripR, cy + stripR), start, sweep, false, bg)
            }
            if (visible(look.sectorTime)) arcText(cv, time, cx, cy, stripR, start + sweep / 2, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.sectorTime.toInt(); textSize = size })
        }
        if (visible(look.durationArc)) {
            val r = inner + size * 0.3f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = look.durationArc.toInt(); strokeWidth = 1.5f * px.coerceAtLeast(1f) }
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, p)
            if (durationLabel) {
                val minutes = (s.event.end - s.event.start) / 60_000
                val label = "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.durationArc.toInt(); textSize = size }
                val labelR = inner + strip * 0.62f
                val labelSweep = (tp.measureText(label) / labelR * 180 / PI).toFloat()
                // У конца дуги; короткий сектор — без подписи.
                if (labelSweep <= sweep * 0.6f) arcText(cv, label, cx, cy, labelR, start + sweep - labelSweep / 2 - 2f, tp)
            }
        }
    }

    /** Сколько букв названия видно в полосе [zone] (внешний и внутренний радиус) — тот же выбор, что в [sectorTitle]. */
    private fun titleShown(s: DialSector, zone: Pair<Float, Float>, base: Float): Int {
        val title = s.event.title
        val (top, bottom) = zone
        val thickness = top - bottom
        if (title.isBlank() || thickness <= 0f) return 0
        val arcLen = (s.sweep * PI / 180 * ((top + bottom) / 2)).toFloat()
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = base }
        if (p.measureText(title) <= arcLen * 0.9f && base <= thickness * 0.55f) return title.length
        p.textSize = minOf(base, arcLen * 0.72f)
        if (p.textSize < base * 0.6f) return 0
        val shown = TextUtils.ellipsize(title, p, (thickness * 0.9f).coerceAtLeast(p.textSize * 2), TextUtils.TruncateAt.END).toString()
        return if (shown == title) title.length else (shown.length - 1).coerceAtLeast(0)
    }

    /** Текст по дуге радиуса [r] с серединой в [centerDeg] (градусы Canvas); внизу круга — не вверх ногами. */
    private fun arcText(cv: Canvas, s: String, cx: Float, cy: Float, r: Float, centerDeg: Float, p: TextPaint) {
        val sweep = (p.measureText(s) / r * 180 / PI).toFloat()
        val fm = p.fontMetrics
        val half = (fm.ascent + fm.descent) / 2 // от базовой линии к середине строки (отрицательное)
        val norm = ((centerDeg % 360f) + 360f) % 360f
        val path = Path()
        if (norm in 0f..180f) {
            // Нижняя половина круга: против часовой, верх букв — к центру, базовая линия снаружи.
            val rr = r - half
            path.addArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), centerDeg + sweep / 2, -sweep)
        } else {
            // Верхняя: по часовой, верх букв — наружу, базовая линия ближе к центру.
            val rr = r + half
            path.addArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), centerDeg - sweep / 2, sweep)
        }
        cv.drawTextOnPath(s, path, 0f, 0f, p)
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

    private fun disc(cv: Canvas, cx: Float, cy: Float, r: Float, color: Long) {
        if (visible(color)) cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toInt() })
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
