package io.github.nullbrash.quazio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.nullbrash.quazio.core.ui.IncomingText
import io.github.nullbrash.quazio.core.ui.QuazioApp

// FragmentActivity, а не ComponentActivity: системному окну входа (BiometricPrompt) нужны фрагменты.
class MainActivity : FragmentActivity() {

    private val incoming = IncomingText()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deviceAuth = AndroidDeviceAuthenticator(this)
        val fileSaver = AndroidFileSaver(this)
        // После поворота экрана intent тот же — текст уже разобран, второй раз не нужен.
        if (savedInstanceState == null) incoming.offer(intent.getStringExtra(EXTRA_QUICK_TEXT))
        setContent {
            QuazioApp(
                versionName = BuildConfig.VERSION_NAME,
                openServices = { AppGraph.services(applicationContext) },
                deviceAuth = deviceAuth,
                fileSaver = fileSaver,
                incoming = incoming,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.offer(intent.getStringExtra(EXTRA_QUICK_TEXT))
    }

    companion object {
        /** Текст из «В учёт Quazio» / «Поделиться» — передаёт [QuickTextActivity]. */
        const val EXTRA_QUICK_TEXT = "io.github.nullbrash.quazio.QUICK_TEXT"
    }
}
