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

    private fun scene(): Bitmap {
        val now = day + 15 * h + 21 * 60_000
        val events = listOf(
            ev("Работа", day + 16 * h, day + 18 * h, 0xFF039BE5),
            ev("Встреча", day + 17 * h, day + 17 * h + h / 2, 0xFF039BE5),
            ev("Без цвета", day + 19 * h, day + 20 * h, null),
            ev("Кино", day + 20 * h, day + 23 * h, 0xFF8E24AA),
            ev("Завтра", day + 25 * h, day + 26 * h, 0xFF33B679),
        )
        val layout = DialLayouts.layout(DialMode.SLIDING_12, events, now, day, day + 24 * h, { t -> (((t - day) / 60_000) % 1440).toInt() })
        return DialBitmap.render(layout, 720, DialStyle(), "15:21", "5.10", "0:39", "ЗАВТРА", distinguish = true)
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
}
