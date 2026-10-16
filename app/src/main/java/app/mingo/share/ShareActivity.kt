package app.mingo.share

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mingo.MingoApp
import app.mingo.R
import app.mingo.ui.findUrl
import app.mingo.ui.theme.MingoTheme

/**
 * Receives links shared from other apps and shows the download options in a sheet on top of
 * the sharing app, so the user never leaves what they were doing.
 */
class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as MingoApp

        val url = intent.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let(::findUrl)
        if (url == null) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Debug builds accept an extra that skips the sheet, so downloads can be tested over adb.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val instant = app.settings.settings.value.instantShareDownload ||
            (debuggable && intent.getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false))

        val extraArgs = if (debuggable) intent.getStringExtra(EXTRA_YTDLP_ARGS)?.split(' ')?.filter { it.isNotBlank() }.orEmpty() else emptyList()

        val viewModel: ShareViewModel by viewModels {
            viewModelFactory { initializer { ShareViewModel(app, url, instant, extraArgs) } }
        }

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            MingoTheme(settings = settings) {
                ShareSheet(viewModel = viewModel, onClose = ::finish)
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_AUTO_DOWNLOAD = "app.mingo.extra.AUTO_DOWNLOAD"
        const val EXTRA_YTDLP_ARGS = "app.mingo.extra.YTDLP_ARGS"
    }
}
