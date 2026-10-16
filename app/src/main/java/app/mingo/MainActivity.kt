package app.mingo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mingo.ui.MingoRoot
import app.mingo.ui.theme.MingoTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as MingoApp
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            MingoTheme(settings = settings) {
                MingoRoot()
            }
        }
    }
}
