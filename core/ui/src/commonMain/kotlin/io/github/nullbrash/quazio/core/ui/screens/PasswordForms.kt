package io.github.nullbrash.quazio.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.nullbrash.quazio.core.lock.CheckResult
import io.github.nullbrash.quazio.core.lock.PasswordVault
import io.github.nullbrash.quazio.core.ui.res.Res
import io.github.nullbrash.quazio.core.ui.SystemBack
import io.github.nullbrash.quazio.core.ui.res.lock_blocked
import io.github.nullbrash.quazio.core.ui.res.lock_code_saved
import io.github.nullbrash.quazio.core.ui.res.lock_code_text
import io.github.nullbrash.quazio.core.ui.res.lock_copied
import io.github.nullbrash.quazio.core.ui.res.lock_copy
import io.github.nullbrash.quazio.core.ui.res.lock_done
import io.github.nullbrash.quazio.core.ui.res.lock_mismatch
import io.github.nullbrash.quazio.core.ui.res.lock_recovery_code
import io.github.nullbrash.quazio.core.ui.res.lock_repeat_password
import io.github.nullbrash.quazio.core.ui.res.lock_too_short
import io.github.nullbrash.quazio.core.ui.res.lock_wrong_code
import io.github.nullbrash.quazio.core.ui.res.lock_wrong_password
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Поле ввода, которое подтверждается Enter и «Готово» на экранной клавиатуре. */
@Composable
internal fun SubmitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSubmit: () -> Unit,
    secret: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= PasswordVault.MAX_LENGTH) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Ascii, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onNext = { onSubmit() }),
        modifier = Modifier.widthIn(max = 360.dp).onPreviewKeyEvent { e ->
            val enter = e.key == Key.Enter || e.key == Key.NumPadEnter
            if (enter && e.type == KeyEventType.KeyDown) { onSubmit(); true } else false
        },
    )
}

/** Текст ошибки проверки пароля или кода. */
@Composable
internal fun checkMessage(result: CheckResult?, wrong: StringResource): String? = when (result) {
    null, CheckResult.Ok -> null
    is CheckResult.Wrong ->
        if (result.blockedForMillis > 0) stringResource(Res.string.lock_blocked, seconds(result.blockedForMillis))
        else stringResource(wrong)
    is CheckResult.Blocked -> stringResource(Res.string.lock_blocked, seconds(result.waitMillis))
}

private fun seconds(millis: Long): String = ((millis + 999) / 1000).toString()

@Composable
internal fun wrongPassword(result: CheckResult?) = checkMessage(result, Res.string.lock_wrong_password)

@Composable
internal fun wrongCode(result: CheckResult?) = checkMessage(result, Res.string.lock_wrong_code)

/** Проверка нового пароля до отправки: длина и совпадение с повтором. */
@Composable
internal fun newPasswordProblem(password: String, repeat: String, touched: Boolean): String? = when {
    !touched -> null
    password.length < PasswordVault.MIN_LENGTH -> stringResource(Res.string.lock_too_short)
    password != repeat -> stringResource(Res.string.lock_mismatch)
    else -> null
}

/** Два поля нового пароля (пароль и повтор). */
@Composable
internal fun NewPasswordFields(
    password: String,
    onPassword: (String) -> Unit,
    repeat: String,
    onRepeat: (String) -> Unit,
    passwordLabel: String,
    onSubmit: () -> Unit,
) {
    SubmitField(password, onPassword, passwordLabel, onSubmit, imeAction = ImeAction.Next)
    SubmitField(repeat, onRepeat, stringResource(Res.string.lock_repeat_password), onSubmit)
}

/**
 * Показ кода восстановления — один раз; закрыть можно, только отметив, что код сохранён.
 * Код можно выделить и скопировать, чтобы сохранить в менеджер паролей.
 */
@Composable
internal fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") // новый Clipboard в Compose пока требует платформенного ClipEntry
    val clipboard = LocalClipboardManager.current
    // «Назад» без этого проходила мимо окна и закрывала приложение: пароль уже задан,
    // а код так и не подтверждён. Пока окно открыто, «назад» не делает ничего.
    SystemBack {}
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.lock_recovery_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.lock_code_text))
                SelectionContainer {
                    Text(code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) {
                    Text(stringResource(if (copied) Res.string.lock_copied else Res.string.lock_copy))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saved, onCheckedChange = { saved = it })
                    Text(stringResource(Res.string.lock_code_saved))
                }
            }
        },
        confirmButton = { TextButton(enabled = saved, onClick = onDone) { Text(stringResource(Res.string.lock_done)) } },
    )
}
