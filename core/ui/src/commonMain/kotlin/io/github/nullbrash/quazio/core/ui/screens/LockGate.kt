package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.lock.CheckResult
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.AppServices
import io.github.nullbrash.quazio.core.ui.DeviceAuthenticator
import io.github.nullbrash.quazio.core.ui.GateMode
import io.github.nullbrash.quazio.core.ui.gateMode
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.res.lock_back
import io.github.nullbrash.quazio.core.ui.res.lock_enter
import io.github.nullbrash.quazio.core.ui.res.lock_forgot
import io.github.nullbrash.quazio.core.ui.res.lock_new_password
import io.github.nullbrash.quazio.core.ui.res.lock_password
import io.github.nullbrash.quazio.core.ui.res.lock_recovery_code
import io.github.nullbrash.quazio.core.ui.res.lock_setup_required
import io.github.nullbrash.quazio.core.ui.res.lock_title
import io.github.nullbrash.quazio.core.ui.res.security_set_password
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Обёртка чувствительного раздела: пока замок закрыт — экран входа вместо
 * содержимого. Как входить, решает [gateMode].
 */
@Composable
fun LockGate(services: AppServices, deviceAuth: DeviceAuthenticator?, content: @Composable () -> Unit) {
    val lockState by services.lock.state.collectAsState()
    var hasPassword by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(services) { hasPassword = withContext(Dispatchers.IO) { services.vault.hasPassword() } }
    val known = hasPassword ?: return

    val mode = gateMode(deviceAuth?.isAvailable() == true, known, services.passwordRequired)
    if (mode == GateMode.OPEN || !lockState.locked) {
        content()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.lock_title), style = MaterialTheme.typography.titleLarge)
        when (mode) {
            GateMode.DEVICE -> DeviceLock(deviceAuth!!) { services.lock.unlock() }
            GateMode.PASSWORD -> PasswordLock(services)
            GateMode.SETUP_REQUIRED -> SetupLock(services) { hasPassword = true }
            GateMode.OPEN -> Unit
        }
    }
}

@Composable
private fun DeviceLock(auth: DeviceAuthenticator, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ask: () -> Unit = { scope.launch { if (auth.authenticate()) onUnlocked() } }
    // Системное окно — сразу при входе в раздел; кнопка — если его закрыли.
    LaunchedEffect(Unit) { ask() }
    Button(onClick = ask) { Text(stringResource(Res.string.lock_enter)) }
}

@Composable
private fun PasswordLock(services: AppServices) {
    val scope = rememberCoroutineScope()
    var recovering by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf<String?>(null) }

    if (!recovering) {
        var password by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<CheckResult?>(null) }
        val submit = {
            if (password.isNotEmpty()) scope.launch {
                val r = withContext(Dispatchers.IO) { services.vault.verify(password) }
                result = r
                if (r == CheckResult.Ok) services.lock.unlock() else password = ""
            }
        }
        SubmitField(password, { password = it; result = null }, stringResource(Res.string.lock_password), { submit() })
        wrongPassword(result)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { submit() }, enabled = password.isNotEmpty()) { Text(stringResource(Res.string.lock_enter)) }
        TextButton(onClick = { recovering = true }) { Text(stringResource(Res.string.lock_forgot)) }
    } else {
        var code by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        var touched by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<CheckResult?>(null) }
        val problem = newPasswordProblem(password, repeat, touched)
        val submit = {
            touched = true
            if (code.isNotBlank() && password.length >= PasswordVault.MIN_LENGTH && password == repeat) scope.launch {
                val (r, issued) = withContext(Dispatchers.IO) { services.vault.recover(code, password) }
                result = r
                if (issued != null) newCode = issued
            }
        }
        SubmitField(code, { code = it; result = null }, stringResource(Res.string.lock_recovery_code), { submit() }, secret = false)
        NewPasswordFields(password, { password = it }, repeat, { repeat = it }, stringResource(Res.string.lock_new_password)) { submit() }
        (problem ?: wrongCode(result))?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Button(onClick = { submit() }) { Text(stringResource(Res.string.lock_enter)) }
        TextButton(onClick = { recovering = false }) { Text(stringResource(Res.string.lock_back)) }
    }

    newCode?.let { code ->
        RecoveryCodeDialog(code) {
            newCode = null
            services.lock.unlock()
        }
    }
}

@Composable
private fun SetupLock(services: AppServices, onPasswordSet: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    val submit = {
        touched = true
        if (password.length >= PasswordVault.MIN_LENGTH && password == repeat) scope.launch {
            code = withContext(Dispatchers.IO) { services.vault.setPassword(password) }
        }
    }
    Text(stringResource(Res.string.lock_setup_required), textAlign = TextAlign.Center)
    NewPasswordFields(password, { password = it }, repeat, { repeat = it }, stringResource(Res.string.lock_new_password)) { submit() }
    newPasswordProblem(password, repeat, touched)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { submit() }) { Text(stringResource(Res.string.security_set_password)) }

    code?.let {
        RecoveryCodeDialog(it) {
            code = null
            services.lock.unlock()
            onPasswordSet()
        }
    }
}
