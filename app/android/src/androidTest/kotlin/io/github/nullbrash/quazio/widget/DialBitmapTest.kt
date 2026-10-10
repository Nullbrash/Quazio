package io.github.nullbrash.quazio.widget

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nullbrash.quazio.feature.calendar.CalendarEvent
import io.github.nullbrash.quazio.feature.calendar.DialLayouts
import io.github.nullbrash.quazio.feature.calendar.DialMode
import io.github.nullbrash.quazio.feature.calendar.DialStyle
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Круг виджета на постоянном наборе дел: хэш картинки — в журнал (`DialBitmapTest`), для сравнения версий. */
@RunWith(AndroidJUnit4::class)
class DialBitmapTest {

    private val h = 3_600_000L
    private val day = 1_790_000_000_000L / (24 * h) * (24 * h)

    private fun ev(t: String, from: Long, to: Long, color: Long?) =
        CalendarEvent(t, "1", t, from, to, false, "UTC", color, null, false, from)

    private fun scene(look: DialStyle = DialStyle(), mode: DialMode = DialMode.SLIDING_12, busy: Boolean = false): Bitmap {
        val now = day + 15 * h + 21 * 60_000
        val events = if (busy) busyDay() else listOf(
            ev("Работа", day + 16 * h, day + 18 * h, 0xFF039BE5),
            ev("Встреча", day + 17 * h, day + 17 * h + h / 2, 0xFF039BE5),
            ev("Без цвета", day + 19 * h, day + 20 * h, null),
            ev("Кино", day + 20 * h, day + 23 * h, 0xFF8E24AA),
            ev("Завтра", day + 25 * h, day + 26 * h, 0xFF33B679),
        )
        val layout = DialLayouts.layout(mode, events, now, day, day + 24 * h, { t -> (((t - day) / 60_000) % 1440).toInt() })
        return DialBitmap.render(layout, 720, look, "15:21", "5.10", "0:39", "ЗАВТРА", distinguish = true, hm = ::hm)
    }

    private fun hm(t: Long): String {
        val m = ((t - day) / 60_000) % 1440
        return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** Перегруженный день: вплотную, внахлёст, короткие, с часовой паузой. */
    private fun busyDay(): List<CalendarEvent> {
        val m = 60_000L
        return listOf(
            ev("Зарядка", day + 7 * h, day + 7 * h + 30 * m, 0xFF33B679),
            ev("Завтрак", day + 7 * h + 30 * m, day + 8 * h, 0xFF33B679),
            ev("Дорога", day + 8 * h, day + 9 * h, 0xFF039BE5),
            ev("Работа", day + 9 * h, day + 13 * h, 0xFF039BE5),
            ev("Созвон", day + 10 * h, day + 10 * h + 15 * m, 0xFFD50000),
            ev("Обед", day + 13 * h, day + 14 * h, 0xFFF6BF26),
            ev("Работа", day + 15 * h, day + 18 * h, 0xFF039BE5),
            ev("Встреча", day + 16 * h, day + 17 * h, 0xFF8E24AA),
            ev("Спорт", day + 19 * h, day + 21 * h, 0xFFE67C73),
        )
    }

    private val allOn = DialStyle(
        outerRing = 0x40FFFFFF, middleRing = 0xFF424242, timeBoundary = 0xFF00E5FF,
        sectorTimeBackground = 0x66000000, sectorTime = 0xFFFFFFFF, durationArc = 0xFFFFFFFF,
        centerBackground = 0xFF212121, dayArcBackground = 0x55FF9800,
    )

    private fun save(name: String, b: Bitmap) {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(ctx.filesDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun md5(b: Bitmap): String {
        val buf = ByteBuffer.allocate(b.byteCount).also { b.copyPixelsToBuffer(it) }
        return MessageDigest.getInstance("MD5").digest(buf.array()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun renderIsStable() {
        val a = scene()
        Log.i("DialBitmapTest", "md5=${md5(a)}")
        kotlin.test.assertEquals(md5(a), md5(scene()), "одна и та же сцена — одна и та же картинка")
    }

    @Test
    fun newElementsShowOnlyWhenColoured() {
        val plain = scene()
        val all = scene(allOn)
        kotlin.test.assertNotEquals(md5(plain), md5(all), "включённые элементы видны")
        val gone = scene(allOn.copy(opacity = 0))
        val px = IntArray(gone.width * gone.height).also { gone.getPixels(it, 0, gone.width, 0, 0, gone.width, gone.height) }
        kotlin.test.assertTrue(px.all { it ushr 24 == 0 }, "общая прозрачность 0 — пустая картинка")
        // Картинки для просмотра глазами (files/ приложения; достаются через run-as).
        save("dial-default.png", plain)
        save("dial-all-12.png", all)
        save("dial-all-24-busy.png", scene(allOn, DialMode.DAY_24, busy = true))
        save("dial-all-12-busy.png", scene(allOn, busy = true))
        save("dial-base-60.png", scene(allOn.copy(allSectorsBase = true, sectorBase = 0xFF8E24AA, opacity = 60)))
    }

    /** Весь виджет (круг и кнопки) — той же разметкой, что на рабочем столе. */
    private fun widget(look: DialStyle): Bitmap {
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val views = DialWidgets.views(ctx.packageName, scene(look), look, showButtons = true)
        val side = (300 * ctx.resources.displayMetrics.density).toInt()
        val view = views.apply(ctx, android.widget.FrameLayout(ctx))
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(side, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(side, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, side, side)
        return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    @Test
    fun buttonsTakeColourAndOpacity() {
        val plain = widget(DialStyle())
        val corner = plain.getPixel(plain.width / 40, plain.height - plain.height / 40)
        // Кнопка по умолчанию — полупрозрачный чёрный кружок, как до настройки (#99000000).
        kotlin.test.assertEquals(0x99, corner ushr 24 and 0xFF, "непрозрачность фона кнопки")
        kotlin.test.assertEquals(0, corner and 0xFFFFFF, "цвет фона кнопки")
        val red = widget(DialStyle(buttons = 0xFFD50000, opacity = 50))
        val c = red.getPixel(red.width / 40, red.height - red.height / 40)
        // ±2 — округление при смешивании слоёв.
        kotlin.test.assertTrue(kotlin.math.abs((c ushr 24 and 0xFF) - 0xFF * 50 / 100) <= 2, "общая прозрачность — и для кнопок: ${c ushr 24 and 0xFF}")
        kotlin.test.assertTrue((c shr 16 and 0xFF) > 150 && (c shr 8 and 0xFF) < 40, "фон кнопки — красный: ${Integer.toHexString(c)}")
        save("widget-default.png", plain)
        save("widget-red-50.png", red)
    }
}

