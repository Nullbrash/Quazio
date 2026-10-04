package io.github.nullbrash.quazio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.nullbrash.quazio.core.ui.QuazioApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { QuazioApp(versionName = BuildConfig.VERSION_NAME) }
    }
}
