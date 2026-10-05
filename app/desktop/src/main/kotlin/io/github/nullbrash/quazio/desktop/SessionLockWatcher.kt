package io.github.nullbrash.quazio.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import kotlin.concurrent.thread

/**
 * Блокировка сеанса Windows (Win+L) → закрыть замок Quazio.
 * Признак: на заблокированном (и на защищённом UAC) рабочем столе OpenInputDesktop
 * недоступен. Опрос раз в 2 с — дешевле, чем подписка через оконные сообщения.
 */
internal object SessionLockWatcher {

    @Suppress("FunctionName")
    private interface User32Desktop : StdCallLibrary {
        fun OpenInputDesktop(flags: Int, inherit: Boolean, desiredAccess: Int): Pointer?
        fun CloseDesktop(desktop: Pointer): Boolean
    }

    private const val DESKTOP_SWITCHDESKTOP = 0x0100

    fun start(onLocked: () -> Unit) {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        thread(isDaemon = true, name = "quazio-session-lock") {
            val user32 = runCatching { Native.load("user32", User32Desktop::class.java) }.getOrNull() ?: return@thread
            var wasLocked = false
            while (true) {
                val desktop = user32.OpenInputDesktop(0, false, DESKTOP_SWITCHDESKTOP)
                val locked = desktop == null
                if (desktop != null) user32.CloseDesktop(desktop)
                if (locked && !wasLocked) onLocked()
                wasLocked = locked
                Thread.sleep(2_000)
            }
        }
    }
}
