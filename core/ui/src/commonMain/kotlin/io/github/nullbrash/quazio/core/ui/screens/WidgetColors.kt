package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.foundation.layout.Spacer
import io.github.nullbrash.quazio.core.ui.res.wc_copied
import io.github.nullbrash.quazio.core.ui.res.wc_copy
import io.github.nullbrash.quazio.core.ui.res.wc_paste
import io.github.nullbrash.quazio.core.ui.res.wc_paste_none
import io.github.nullbrash.quazio.core.ui.res.wc_pasted
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.nullbrash.quazio.core.ui.LocalWidgetPreview
import io.github.nullbrash.quazio.core.ui.LocalWidgetUpdater
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.wc_all_base
import io.github.nullbrash.quazio.core.ui.res.wc_brightness
import io.github.nullbrash.quazio.core.ui.res.wc_c_background
import io.github.nullbrash.quazio.core.ui.res.wc_c_buttons
import io.github.nullbrash.quazio.core.ui.res.wc_c_center_background
import io.github.nullbrash.quazio.core.ui.res.wc_c_center_text
import io.github.nullbrash.quazio.core.ui.res.wc_c_day_arc
import io.github.nullbrash.quazio.core.ui.res.wc_c_day_arc_background
import io.github.nullbrash.quazio.core.ui.res.wc_c_day_text
import io.github.nullbrash.quazio.core.ui.res.wc_c_duration_arc
import io.github.nullbrash.quazio.core.ui.res.wc_c_event_text
import io.github.nullbrash.quazio.core.ui.res.wc_c_hand
import io.github.nullbrash.quazio.core.ui.res.wc_c_hour_numbers
import io.github.nullbrash.quazio.core.ui.res.wc_c_hour_ticks
import io.github.nullbrash.quazio.core.ui.res.wc_c_middle_ring
import io.github.nullbrash.quazio.core.ui.res.wc_c_outer_ring
import io.github.nullbrash.quazio.core.ui.res.wc_c_sector_base
import io.github.nullbrash.quazio.core.ui.res.wc_c_sector_time
import io.github.nullbrash.quazio.core.ui.res.wc_c_sector_time_background
import io.github.nullbrash.quazio.core.ui.res.wc_c_time_boundary
import io.github.nullbrash.quazio.core.ui.res.wc_c_waiting_arc
import io.github.nullbrash.quazio.core.ui.res.wc_c_waiting_text
import io.github.nullbrash.quazio.core.ui.res.wc_g_background
import io.github.nullbrash.quazio.core.ui.res.wc_g_center
import io.github.nullbrash.quazio.core.ui.res.wc_g_hands
import io.github.nullbrash.quazio.core.ui.res.wc_g_hours
import io.github.nullbrash.quazio.core.ui.res.wc_g_other
import io.github.nullbrash.quazio.core.ui.res.wc_g_sectors
import io.github.nullbrash.quazio.core.ui.res.wc_g_waiting
import io.github.nullbrash.quazio.core.ui.res.wc_opacity
import io.github.nullbrash.quazio.core.ui.res.wc_pick
import io.github.nullbrash.quazio.core.ui.res.wc_reset
import io.github.nullbrash.quazio.core.ui.res.wc_reset_confirm
import io.github.nullbrash.quazio.core.ui.res.wc_title
import io.github.nullbrash.quazio.core.ui.res.wc_transparency
import io.github.nullbrash.quazio.core.ui.res.wc_used
import io.github.nullbrash.quazio.feature.calendar.DialStyle
import io.github.nullbrash.quazio.feature.calendar.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Группы и строки — как в образце пользователя; ключи — [DialStyle.COLORS]. */
private val GROUPS: List<Pair<StringResource, List<Pair<String, StringResource>>>> = listOf(
    Res.string.wc_g_background to listOf("outer_ring" to Res.string.wc_c_outer_ring, "middle_ring" to Res.string.wc_c_middle_ring, "background" to Res.string.wc_c_background),
    Res.string.wc_g_hands to listOf("hand" to Res.string.wc_c_hand, "time_boundary" to Res.string.wc_c_time_boundary),
    Res.string.wc_g_sectors to listOf(
        "sector_base" to Res.string.wc_c_sector_base, "sector_time_background" to Res.string.wc_c_sector_time_background,
        "event_text" to Res.string.wc_c_event_text, "sector_time" to Res.string.wc_c_sector_time, "duration_arc" to Res.string.wc_c_duration_arc,
    ),
    Res.string.wc_g_waiting to listOf("waiting_arc" to Res.string.wc_c_waiting_arc, "waiting_text" to Res.string.wc_c_waiting_text),
    Res.string.wc_g_center to listOf(
        "center_background" to Res.string.wc_c_center_background, "center_text" to Res.string.wc_c_center_text,
        "day_arc" to Res.string.wc_c_day_arc, "day_arc_background" to Res.string.wc_c_day_arc_background, "day_text" to Res.string.wc_c_day_text,
    ),
    Res.string.wc_g_hours to listOf("hour_ticks" to Res.string.wc_c_hour_ticks, "hour_numbers" to Res.string.wc_c_hour_numbers),
    Res.string.wc_g_other to listOf("buttons" to Res.string.wc_c_buttons),
)

/**
 * «Цвета виджета» на весь экран: превью сверху (тем же рисовальщиком, что виджет, на
 * сегодняшнем дне), ниже — группы цветов. Сохраняется сразу; виджет перерисовывается сам.
 * [calendarColors] — цвета показываемых календарей для ряда «Уже используются».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetColorsScreen(prefs: WidgetPrefs, calendarColors: List<Long>, onClose: () -> Unit) {
    val preview = LocalWidgetPreview.current
    val update = LocalWidgetUpdater.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(prefs.style) }
    var picking by remember { mutableStateOf<DialStyle.ColorField?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(style.opacity.toFloat()) }
    var notice by remember { mutableStateOf<StringResource?>(null) }
    @Suppress("DEPRECATION") // новый Clipboard в Compose пока требует платформенного ClipEntry
    val clipboard = LocalClipboardManager.current

    fun save(next: DialStyle) {
        style = next
        scope.launch {
            withContext(Dispatchers.IO) { prefs.style = next }
            update?.invoke()
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_cancel)) }
                    // Надпись уступает место кнопкам пресетов (решение пользователя): сжимается с многоточием.
                    Text(stringResource(Res.string.wc_title), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Свой вариант — строкой у пользователя: переживает переустановку.
                    IconButton(onClick = { clipboard.setText(AnnotatedString(style.share())); notice = Res.string.wc_copied }) {
                        Icon(CopyIcon, contentDescription = stringResource(Res.string.wc_copy))
                    }
                    IconButton(onClick = {
                        val pasted = DialStyle.parseShared(clipboard.getText()?.text)
                        if (pasted == null) notice = Res.string.wc_paste_none
                        else { opacity = pasted.opacity.toFloat(); save(pasted); notice = Res.string.wc_pasted }
                    }) { Icon(PasteIcon, contentDescription = stringResource(Res.string.wc_paste)) }
                    IconButton(onClick = { confirmReset = true }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.wc_reset)) }
                }
                notice?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                }
                if (preview != null) Preview(style, preview)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    GROUPS.forEach { (group, rows) ->
                        Text(stringResource(group), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        rows.forEach { (key, name) ->
                            val field = DialStyle.COLORS.first { it.key == key }
                            Row(Modifier.fillMaxWidth().clickable { picking = field }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(name), modifier = Modifier.weight(1f))
                                ColorDot(field.get(style), 28.dp)
                            }
                            if (key == "sector_base") {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(Res.string.wc_all_base), modifier = Modifier.weight(1f))
                                    Switch(style.allSectorsBase, { save(style.copy(allSectorsBase = it)) })
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                    Text(stringResource(Res.string.wc_opacity, opacity.roundToInt()), modifier = Modifier.padding(top = 16.dp))
                    // Сохраняется по отпусканию — не перерисовывать виджет на каждый сдвиг.
                    Slider(opacity, { opacity = it }, valueRange = 0f..100f, steps = 19, onValueChangeFinished = { save(style.copy(opacity = opacity.roundToInt())) })
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    picking?.let { field ->
        val used = (DialStyle.COLORS.map { it.get(style) } + calendarColors)
            .map { it and 0xFFFFFFFFL }.filter { (it ushr 24) != 0L }.distinct()
        ColorPickerDialog(field.get(style), used, onPick = { save(field.set(style, it)); picking = null }, onDismiss = { picking = null })
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(Res.string.wc_reset_confirm)) },
            confirmButton = { TextButton(onClick = { confirmReset = false; opacity = 100f; save(DialStyle()) }) { Text(stringResource(Res.string.lock_done)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Превью виджета на «шахматке» — видно, где он прозрачный. */
@Composable
private fun Preview(style: DialStyle, render: (DialStyle, Int) -> ImageBitmap?) {
    val side = 240.dp
    val px = with(LocalDensity.current) { side.roundToPx() }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(style) { image = withContext(Dispatchers.IO) { runCatching { render(style, px) }.getOrNull() } }
    Box(Modifier.fillMaxWidth().height(side + 16.dp).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(side)) { checker(18f) }
        image?.let { Image(it, contentDescription = null, modifier = Modifier.size(side)) }
    }
}

/** Кружок цвета; под прозрачным — «шахматка». */
@Composable
private fun ColorDot(argb: Long, size: androidx.compose.ui.unit.Dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(size).clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            checker(this.size.minDimension / 4)
            drawRect(argbColor(argb))
        }
    }
}

/**
 * Выбор цвета по образцу: круг из точек (оттенок по кругу, к центру — бледнее), яркость,
 * прозрачность; сверху — «Уже используются» (решение пользователя).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPickerDialog(initial: Long, used: List<Long>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val start = remember { Hsva.of(initial) }
    var h by remember { mutableFloatStateOf(start.h) }
    var s by remember { mutableFloatStateOf(start.s) }
    var v by remember { mutableFloatStateOf(start.v) }
    var a by remember { mutableFloatStateOf(start.a) }
    val current = Hsva(h, s, v, a).argb()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.wc_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (used.isNotEmpty()) {
                    Text(stringResource(Res.string.wc_used), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        used.forEachIndexed { i, c ->
                            Box(Modifier.testTag("used-$i")) { ColorDot(c, 30.dp, selected = c == current) { Hsva.of(c).let { h = it.h; s = it.s; v = it.v; a = it.a } } }
                        }
                    }
                }
                DotWheel(h, s, v, Modifier.fillMaxWidth().aspectRatio(1f)) { nh, ns -> h = nh; s = ns; if (v < 0.2f) v = 1f; if (a < 0.2f) a = 1f }
                Text(stringResource(Res.string.wc_brightness), style = MaterialTheme.typography.labelLarge)
                GradientSlider(v, { v = it }, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f))), checker = false)
                Text(stringResource(Res.string.wc_transparency), style = MaterialTheme.typography.labelLarge)
                GradientSlider(a, { a = it }, Brush.horizontalGradient(listOf(Color.Transparent, Color.hsv(h, s, v))), checker = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorDot(initial, 36.dp)
                    Text("→")
                    ColorDot(current, 36.dp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(current) }) { Text(stringResource(Res.string.lock_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

// Material Icons «content_copy» и «content_paste» (Apache-2.0): в базовом наборе их нет.
private val CopyIcon = icon("M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
private val PasteIcon = icon("M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z")

private fun icon(path: String) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

private const val RINGS = 7

/** Точки круга: центр — белый, кольцо k — 6·k точек с насыщенностью k / [RINGS]. */
private fun wheelDots(): List<Pair<Float, Float>> = buildList {
    add(0f to 0f)
    for (k in 1..RINGS) for (i in 0 until 6 * k) add(360f * i / (6 * k) to k.toFloat() / RINGS)
}

@Composable
private fun DotWheel(h: Float, s: Float, v: Float, modifier: Modifier, onPick: (Float, Float) -> Unit) {
    val dots = remember { wheelDots() }
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val nearest = dots.minBy { (dh, ds) -> dist(dh, ds, h, s) }
    BoxWithConstraints(modifier.testTag("color-wheel")) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { p ->
                    val r = size.width / 2f
                    val dx = p.x - r
                    val dy = p.y - r
                    val ns = (hypot(dx, dy) / (r * 0.92f)).coerceIn(0f, 1f)
                    val nh = ((atan2(dy, dx) * 180 / PI).toFloat() + 360f) % 360f
                    val (dh, ds) = dots.minBy { (dh, ds) -> dist(dh, ds, nh, ns) }
                    onPick(dh, ds)
                }
            },
        ) {
            val r = size.minDimension / 2f * 0.92f
            val c = Offset(size.width / 2, size.height / 2)
            val dot = r / RINGS * 0.42f
            for ((dh, ds) in dots) {
                val rad = dh * PI / 180
                val p = Offset(c.x + (r * ds * cos(rad)).toFloat(), c.y + (r * ds * sin(rad)).toFloat())
                val chosen = dh == nearest.first && ds == nearest.second
                drawCircle(Color.hsv(dh, ds, v.coerceAtLeast(0.05f)), radius = if (chosen) dot * 1.45f else dot, center = p)
                if (chosen) drawCircle(selectedColor, radius = dot * 1.45f, center = p, style = Stroke(width = dot * 0.25f))
            }
        }
    }
}

/** Расстояние между точками круга в координатах «насыщенность — радиус, оттенок — угол». */
private fun dist(h1: Float, s1: Float, h2: Float, s2: Float): Float {
    val a1 = h1 * PI / 180
    val a2 = h2 * PI / 180
    return hypot((s1 * cos(a1) - s2 * cos(a2)).toFloat(), (s1 * sin(a1) - s2 * sin(a2)).toFloat())
}

@Composable
private fun GradientSlider(value: Float, onChange: (Float) -> Unit, brush: Brush, checker: Boolean) {
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 10.dp).clip(CircleShape)) {
            if (checker) checker(size.height / 2)
            drawRect(brush)
        }
        Slider(value, onChange, valueRange = 0f..1f, colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
    }
}

private fun DrawScope.checker(cell: Float) {
    val light = Color(0xFFE0E0E0)
    val dark = Color(0xFFB0B0B0)
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(dark, Offset(x, y), Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

private fun argbColor(argb: Long) = Color((argb and 0xFFFFFFFFL).toInt())

/** Оттенок 0…360, насыщенность, яркость и прозрачность 0…1 — для выбора цвета. */
internal data class Hsva(val h: Float, val s: Float, val v: Float, val a: Float) {
    fun argb(): Long {
        val c = Color.hsv(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        val ch = { x: Float -> (x * 255).roundToInt().coerceIn(0, 255).toLong() }
        return (ch(a) shl 24) or (ch(c.red) shl 16) or (ch(c.green) shl 8) or ch(c.blue)
    }

    companion object {
        fun of(argb: Long): Hsva {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            val h = when {
                d == 0f -> 0f
                max == r -> 60f * (((g - b) / d) % 6f)
                max == g -> 60f * ((b - r) / d + 2f)
                else -> 60f * ((r - g) / d + 4f)
            }.let { (it + 360f) % 360f }
            return Hsva(h, if (max == 0f) 0f else d / max, max, a)
        }
    }
}
