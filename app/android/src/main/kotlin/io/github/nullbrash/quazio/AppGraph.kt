package io.github.nullbrash.quazio

import android.content.Context
import android.os.Build
import io.github.nullbrash.quazio.core.accounts.AccountService
import io.github.nullbrash.quazio.core.db.AndroidDatabase
import io.github.nullbrash.quazio.core.db.AndroidKeystoreKeyStore
import io.github.nullbrash.quazio.core.ui.AppServices

/**
 * Службы основного процесса — лениво, при первом обращении (не в Application:
 * процесс поднимается и ради фоновых частей, запуск должен оставаться лёгким).
 * Процесс клавиатуры `:ime` сюда не обращается — основная база в нём не открывается.
 */
object AppGraph {

    @Volatile private var services: AppServices? = null

    fun services(context: Context): AppServices =
        services ?: synchronized(this) { services ?: create(context.applicationContext).also { services = it } }

    private fun create(context: Context): AppServices = AppServices(
        accounts = AccountService(AndroidDatabase.open(context, AndroidKeystoreKeyStore(context)), System::currentTimeMillis),
        deviceName = Build.MODEL,
        platform = "android",
    )
}
