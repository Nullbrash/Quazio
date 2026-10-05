package io.github.nullbrash.quazio.desktop

import io.github.nullbrash.quazio.core.ui.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Окно Windows «Сохранить как» (AWT): модальное, вызывается в потоке интерфейса. */
internal class DesktopFileSaver(private val owner: Frame) : FileSaver {

    override suspend fun save(suggestedName: String, bytes: ByteArray): Boolean {
        val target = withContext(Dispatchers.Main) {
            val dialog = FileDialog(owner, "Сохранить", FileDialog.SAVE).apply {
                file = suggestedName
                isVisible = true
            }
            val name = dialog.file ?: return@withContext null
            File(dialog.directory, name)
        } ?: return false
        withContext(Dispatchers.IO) { target.writeBytes(bytes) }
        return true
    }
}
