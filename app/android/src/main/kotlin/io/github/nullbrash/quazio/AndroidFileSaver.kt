package io.github.nullbrash.quazio

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.github.nullbrash.quazio.core.ui.FileSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Системный выбор места сохранения (Storage Access Framework) — без разрешений на файлы.
 * Создавать в onCreate: регистрация результата должна произойти до старта Activity.
 */
internal class AndroidFileSaver(private val activity: ComponentActivity) : FileSaver {

    private var pending: CompletableDeferred<Uri?>? = null
    private val launcher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            pending?.complete(uri)
            pending = null
        }

    override suspend fun save(suggestedName: String, bytes: ByteArray): Boolean {
        val result = CompletableDeferred<Uri?>()
        pending = result
        launcher.launch(suggestedName)
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Не удалось открыть файл")
        }
        return true
    }
}
