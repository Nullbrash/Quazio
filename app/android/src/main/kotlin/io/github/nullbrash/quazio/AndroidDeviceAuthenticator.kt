package io.github.nullbrash.quazio

import android.app.KeyguardManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.nullbrash.quazio.core.ui.DeviceAuthenticator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Вход через блокировку экрана: отпечаток/лицо или PIN-код устройства — что настроено.
 * BIOMETRIC_WEAK | DEVICE_CREDENTIAL — единственное сочетание, которое androidx.biometric
 * поддерживает на всех версиях от Android 8 (сильная биометрия с PIN — только с 11).
 */
class AndroidDeviceAuthenticator(private val activity: FragmentActivity) : DeviceAuthenticator {

    override fun isAvailable(): Boolean =
        activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    override suspend fun authenticate(): Boolean = suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                // Отмена, слишком много попыток и т. п. Одиночная неудача — onAuthenticationFailed:
                // окно остаётся открытым, ждём следующей попытки.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.auth_title))
            .setSubtitle(activity.getString(R.string.auth_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
