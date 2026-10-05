package io.github.nullbrash.quazio

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * «В учёт Quazio» из меню выделенного текста и из «Поделиться»: передаёт текст основному
 * окну и сразу закрывается. Отдельное окно без интерфейса — чтобы Quazio не открывался
 * внутри задачи чужого приложения (меню выделения запускает окно в задаче вызвавшего).
 */
class QuickTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT) ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_QUICK_TEXT, text.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
    }
}
