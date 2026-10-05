package io.github.nullbrash.quazio

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.nullbrash.quazio.core.ui.CalendarAccess
import io.github.nullbrash.quazio.feature.calendar.AndroidCalendarSource
import kotlinx.coroutines.CompletableDeferred

/** Системный запрос доступа к календарям. Создавать в onCreate: до старта Activity. */
internal class AndroidCalendarAccess(private val activity: ComponentActivity) : CalendarAccess {

    private var pending: CompletableDeferred<Boolean>? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending?.complete(result.values.all { it })
        pending = null
    }

    override fun granted(): Boolean = AndroidCalendarSource.hasPermission(activity)

    override suspend fun request(): Boolean {
        if (granted()) return true
        val result = CompletableDeferred<Boolean>()
        pending = result
        launcher.launch(AndroidCalendarSource.PERMISSIONS)
        return result.await()
    }
}
