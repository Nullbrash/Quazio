package io.github.nullbrash.quazio.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Сохранение файла, выбранного пользователем (выгрузка CSV и т. п.): на ПК — окно
 * «Сохранить как», на Android — системный выбор места. false — пользователь отменил.
 */
fun interface FileSaver {
    suspend fun save(suggestedName: String, bytes: ByteArray): Boolean
}

/** Платформа даёт его через [QuazioApp]; null — сохранять файлы негде (пункты выгрузки скрыты). */
val LocalFileSaver = staticCompositionLocalOf<FileSaver?> { null }
