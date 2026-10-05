package io.github.nullbrash.quazio.core.ui.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.nullbrash.quazio.core.ui.screens.finance.colorOf
import io.github.nullbrash.quazio.feature.calendar.DialLayout
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Циферблат дня по образцу виджета Sectograph пользователя: события — цветные сектора
 * (пересекающиеся — на разных «дорожках»), красная стрелка — сейчас, внутренняя дуга —
 * сколько осталось до следующего события, метка «завтра» — где начинается завтрашний день.
 */
@Composable
internal fun DayDial(
    layout: DialLayout,
    centerTop: String,
    centerBottom: String,
    untilNextText: String?,
    tomorrowText: String,
    description: String,
    modifier: Modifier = Modifier,
    /** Маленькое превью (окно события): подписи только 0, 6, 12, 18 и мелкий центр. */
    compact: Boolean = false,
    /** Стрелку тянут по кругу: на сколько градусов повернули (по часовой — плюс). */
    onDrag: ((Float) -> Unit)? = null,
    /** Нажатие на центр круга — вернуться к «сейчас». */
    onCenterTap: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val face = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val untilColor = MaterialTheme.colorScheme.onSurface
    val tomorrowColor = Color(0xFFFF9800)
    val handColor = Color(0xFFE53935)

    var gestures: Modifier = Modifier
    if (onDrag != null) gestures = gestures.pointerInput(Unit) {
        // Поворот пальца вокруг центра — угол между прошлой и новой точкой касания.
        detectDragGestures { change, _ ->
            val c = Offset(size.width / 2f, size.height / 2f)
            val before = degreesAround(c, change.previousPosition)
            val after = degreesAround(c, change.position)
            var delta = after - before
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            change.consume()
            onDrag(delta)
        }
    }
    if (onCenterTap != null) gestures = gestures.pointerInput(Unit) {
        detectTapGestures { p ->
            val c = Offset(size.width / 2f, size.height / 2f)
            if ((p - c).getDistance() < size.width * 0.15f) onCenterTap()
        }
    }
    Canvas(modifier.aspectRatio(1f).then(gestures).semantics { contentDescription = description }) {
        val c = center
        val radius = size.minDimension / 2f * 0.80f
        drawCircle(face, radius, c)

        // Часовые линии.
        val ticks = layout.labels.size.coerceAtLeast(12)
        for (i in 0 until (if (ticks == 24) 24 else 12)) {
            val a = angle(i * 360f / (if (ticks == 24) 24 else 12))
            drawLine(ink.copy(alpha = 0.25f), point(c, radius * 0.30f, a), point(c, radius, a), strokeWidth = 1f)
        }

        // Сектора событий: «дорожка» 0 — внешняя.
        val bandOuter = radius * 0.97f
        val bandInner = radius * 0.40f
        val laneWidth = (bandOuter - bandInner) / layout.lanes
        for (s in layout.sectors) {
            val mid = bandOuter - laneWidth * (s.lane + 0.5f)
            val color = colorOf(s.event.color)
            drawArc(
                color = color,
                startAngle = s.startAngle - 90f,
                sweepAngle = s.sweep.coerceAtLeast(1f),
                useCenter = false,
                topLeft = Offset(c.x - mid, c.y - mid),
                size = Size(mid * 2, mid * 2),
                style = Stroke(width = laneWidth * 0.92f, cap = StrokeCap.Butt),
            )
            if (s.sweep >= 14f && s.event.title.isNotBlank()) {
                val textAt = point(c, mid, angle(s.startAngle + s.sweep / 2))
                drawCentered(measurer, s.event.title, textAt, TextStyle(color = Color.White, fontSize = 11.sp), maxWidth = (laneWidth * 1.6f).toInt().coerceAtLeast(40))
            }
        }

        // До следующего события — белая дуга внутри.
        layout.untilNext?.let { u ->
            val r = radius * 0.33f
            drawArc(
                untilColor, u.fromAngle - 90f, u.sweep.coerceAtLeast(1f), false,
                Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width = radius * 0.04f, cap = StrokeCap.Round),
            )
            // Подпись — у конца дуги (начала события), а не посередине: короткая дуга почти у стрелки.
            untilNextText?.let { drawCentered(measurer, it, point(c, r * 1.25f, angle(u.fromAngle + u.sweep + 12f)), TextStyle(color = labelColor, fontSize = 11.sp)) }
        }

        // Граница завтрашнего дня.
        layout.tomorrowAngle?.let { a ->
            val r = radius * 0.27f
            drawArc(tomorrowColor, a - 90f - 20f, 40f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width = radius * 0.03f))
            drawCentered(measurer, tomorrowText, point(c, r * 1.35f, angle(a)), TextStyle(color = tomorrowColor, fontSize = 9.sp, fontWeight = FontWeight.Bold))
        }

        // Подписи часов — снаружи круга.
        val labelStyle = TextStyle(color = labelColor, fontSize = if (compact) 8.sp else if (layout.labels.size > 12) 9.sp else 13.sp, fontWeight = FontWeight.Bold)
        for (l in layout.labels) if (!compact || l.hour % 6 == 0) drawCentered(measurer, l.hour.toString(), point(c, radius * 1.13f, angle(l.angle)), labelStyle)

        // Стрелка «сейчас».
        layout.nowAngle?.let { a ->
            drawLine(handColor, point(c, radius * 0.30f, angle(a)), point(c, radius * 1.03f, angle(a)), strokeWidth = radius * 0.025f, cap = StrokeCap.Round)
        }

        val big = if (compact) 12.sp else 22.sp
        val small = if (compact) 9.sp else 13.sp
        drawCentered(measurer, centerTop, Offset(c.x, c.y - radius * (if (compact) 0.10f else 0.06f)), TextStyle(color = labelColor, fontSize = big, fontWeight = FontWeight.Bold))
        drawCentered(measurer, centerBottom, Offset(c.x, c.y + radius * (if (compact) 0.16f else 0.12f)), TextStyle(color = labelColor, fontSize = small))
    }
}

/** Угол точки вокруг центра: 0 — сверху, по часовой стрелке, 0…360. */
private fun degreesAround(c: Offset, p: Offset): Float =
    ((atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble()) * 180.0 / PI + 90.0 + 360.0) % 360.0).toFloat()

/** Угол циферблата (0 — сверху, по часовой) → радианы для синуса/косинуса. */
private fun angle(degreesFromTop: Float): Double = (degreesFromTop - 90.0) * PI / 180.0

private fun point(c: Offset, r: Float, rad: Double) = Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat())

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle, maxWidth: Int = Int.MAX_VALUE) {
    val m = measurer.measure(text, style, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxWidth))
    drawText(m, topLeft = Offset(at.x - m.size.width / 2f, at.y - m.size.height / 2f))
}
