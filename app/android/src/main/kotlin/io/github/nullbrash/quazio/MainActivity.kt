package io.github.nullbrash.quazio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.nullbrash.quazio.core.ui.QuazioApp

// FragmentActivity, а не ComponentActivity: системному окну входа (BiometricPrompt) нужны фрагменты.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deviceAuth = AndroidDeviceAuthenticator(this)
        setContent {
            QuazioApp(
                versionName = BuildConfig.VERSION_NAME,
                openServices = { AppGraph.services(applicationContext) },
                deviceAuth = deviceAuth,
            )
        }
    }
}
