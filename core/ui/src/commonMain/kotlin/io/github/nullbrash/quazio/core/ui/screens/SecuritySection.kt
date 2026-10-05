package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.lock.CheckResult
import io.github.nullbrash.quazio.core.lock.LockTimeout
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.DeviceAuthenticator
import io.github.nullbrash.quazio.core.ui.GateMode
import io.github.nullbrash.quazio.core.ui.gateMode
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.action_cancel
import io.github.nullbrash.quazio.core.ui.res.lock_current_password
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.lock_new_password
import io.github.nullbrash.quazio.core.ui.res.security_change_password
import io.github.nullbrash.quazio.core.ui.res.security_mode_device
import io.github.nullbrash.quazio.core.ui.res.security_mode_open
import io.github.nullbrash.quazio.core.ui.res.security_mode_password
import io.github.nullbrash.quazio.core.ui.res.security_mode_setup
import io.github.nullbrash.quazio.core.ui.res.security_remove_password
import io.github.nullbrash.quazio.core.ui.res.security_set_password
import io.github.nullbrash.quazio.core.ui.res.security_timeout
import io.github.nullbrash.quazio.core.ui.res.security_title
import io.github.nullbrash.quazio.core.ui.res.timeout_1
import io.github.nullbrash.quazio.core.ui.res.timeout_15
import io.github.nullbrash.quazio.core.ui.res.timeout_5
import io.github.nullbrash.quazio.core.ui.res.timeout_immediate
import io.github.nullbrash.quazio.core.ui.res.timeout_never
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class PasswordDialog { SET, CHANGE, REMOVE }

/** Раздел «Защита» в настройках. Смена и удаление пароля — только после ввода текущего. */
@Composable
fun SecuritySection(services: AppServices, deviceAuth: DeviceAuthenticator?) {
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var hasPassword by remember { mutableStateOf<Boolean?>(null) }
    var timeout by remember { mutableStateOf(services.lock.state.value.timeout) }
    var dialog by remember { mutableStateOf<PasswordDialog?>(null) }
    var issuedCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) { hasPassword = withContext(Dispatchers.IO) { services.vault.hasPassword() } }
    val known = hasPassword ?: return
    val deviceAvailable = deviceAuth?.isAvailable() == true
    val mode = gateMode(deviceAvailable, known, services.passwordRequired)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.security_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when (mode) {
                    GateMode.DEVICE -> Res.string.security_mode_device
                    GateMode.PASSWORD -> Res.string.security_mode_password
                    GateMode.SETUP_REQUIRED -> Res.string.security_mode_setup
                    GateMode.OPEN -> Res.string.security_mode_open
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        // Пароль нужен, только когда нет блокировки экрана устройства.
        if (!deviceAvailable) {
            Row {
                if (!known) {
                    TextButton(onClick = { dialog = PasswordDialog.SET }) { Text(stringResource(Res.string.security_set_password)) }
                } else {
                    TextButton(onClick = { dialog = PasswordDialog.CHANGE }) { Text(stringResource(Res.string.security_change_password)) }
                    if (!services.passwordRequired) {
                        TextButton(onClick = { dialog = PasswordDialog.REMOVE }) { Text(stringResource(Res.string.security_remove_password)) }
                    }
                }
            }
        }

        if (mode != GateMode.OPEN) {
            Text(stringResource(Res.string.security_timeout), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            LockTimeout.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        timeout = option
                        services.lock.setTimeout(option)
                        scope.launch { withContext(Dispatchers.IO) { services.vault.timeout = option } }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == timeout, onClick = null)
                    Text(stringResource(option.label), modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                }
            }
        }
    }

    dialog?.let { kind ->
        PasswordChangeDialog(
            kind = kind,
            vault = services.vault,
            onDismiss = { dialog = null },
            onDone = { code ->
                dialog = null
                issuedCode = code
                reload++
            },
        )
    }
    issuedCode?.let { RecoveryCodeDialog(it) { issuedCode = null } }
}

private val LockTimeout.label: StringResource
    get() = when (this) {
        LockTimeout.IMMEDIATE -> Res.string.timeout_immediate
        LockTimeout.MIN_1 -> Res.string.timeout_1
        LockTimeout.MIN_5 -> Res.string.timeout_5
        LockTimeout.MIN_15 -> Res.string.timeout_15
        LockTimeout.NEVER -> Res.string.timeout_never
    }

/** Задать / сменить / убрать пароль. [onDone] получает новый код восстановления (null — пароль убран). */
@Composable
private fun PasswordChangeDialog(kind: PasswordDialog, vault: PasswordVault, onDismiss: () -> Unit, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    val needsCurrent = kind != PasswordDialog.SET
    val needsNew = kind != PasswordDialog.REMOVE

    val submit = {
        touched = true
        val newOk = !needsNew || (password.length >= PasswordVault.MIN_LENGTH && password == repeat)
        if (newOk && (!needsCurrent || current.isNotEmpty())) scope.launch {
            val check = if (needsCurrent) withContext(Dispatchers.IO) { vault.verify(current) } else CheckResult.Ok
            result = check
            if (check == CheckResult.Ok) {
                val code = withContext(Dispatchers.IO) {
                    if (needsNew) vault.setPassword(password) else { vault.removePassword(); null }
                }
                onDone(code)
            } else {
                current = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (kind) {
                        PasswordDialog.SET -> Res.string.security_set_password
                        PasswordDialog.CHANGE -> Res.string.security_change_password
                        PasswordDialog.REMOVE -> Res.string.security_remove_password
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needsCurrent) SubmitField(current, { current = it; result = null }, stringResource(Res.string.lock_current_password), { submit() })
                if (needsNew) NewPasswordFields(password, { password = it }, repeat, { repeat = it }, stringResource(Res.string.lock_new_password)) { submit() }
                ((if (needsNew) newPasswordProblem(password, repeat, touched) else null) ?: wrongPassword(result))
                    ?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text(stringResource(Res.string.lock_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
