package app.mingo.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mingo.MingoApp
import app.mingo.R
import app.mingo.engine.MingoEngine
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.IconBadge
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.SectionLabel
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons

private const val DEVELOPER = "suhaib (sheed) 🦩"
private const val EMAIL = "salmqalh8@gmail.com"
private const val GITHUB_USER = "sheed-x"
private const val GITHUB_URL = "https://github.com/$GITHUB_USER"

/**
 * The About page. It is intentionally English-only and always left-to-right, whatever language
 * the rest of the app is shown in.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AboutContent(onBack)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MingoApp
    val engineState by app.engine.state.collectAsStateWithLifecycle()
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                MingoIcon(MingoIcons.Back, contentDescription = "Back", size = 20.dp)
            }
        }

        // Header: logo on a scalloped badge, name, version and tagline.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(128.dp)
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.mingo_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Mingo", style = MaterialTheme.typography.displaySmallEmphasized)
            Text(
                "Version $version",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "A clean, private way to save videos and music from the web.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        SectionLabel("Developer")
        Group {
            LinkRow(MingoIcons.User, "Made by", DEVELOPER, onClick = null)
            LinkRow(MingoIcons.Mail, "Email", EMAIL) { sendEmail(context, subject = "Mingo $version") }
            LinkRow(MingoIcons.Github, "GitHub", "github.com/$GITHUB_USER") { openUrl(context, GITHUB_URL) }
        }

        SectionLabel("How it works")
        Group {
            Step(1, "Share or paste a link", "Use Share from any app, or paste a link on the Home screen.")
            Step(2, "Mingo reads the page", "The built-in yt-dlp engine finds every quality and format the site offers.")
            Step(3, "You pick what you want", "Video or audio, resolution, format and extras like cover art.")
            Step(4, "It downloads in the background", "FFmpeg merges and converts the file, then it's saved to Download/Mingo and your Library.")
        }

        SectionLabel("Good to know")
        Group {
            InfoRow(MingoIcons.Shield, "Private by design", "No accounts, no ads, no tracking. Everything stays on your device.")
            InfoRow(MingoIcons.Refresh, "Always up to date", "The download engine updates itself daily, because sites change often.")
            InfoRow(MingoIcons.Folder, "Where files go", "Download/Mingo on your phone's storage.")
            InfoRow(
                MingoIcons.Phone,
                "Compatibility",
                "Android 7.0 (Nougat) and newer, optimized for Android 16. " +
                    "Runs on ARM and x86 phones and tablets.\n" +
                    "This device: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
            EngineRow(engineState, onUpdate = app.engine::updateNow)
        }

        SectionLabel("Open source")
        Group {
            InfoRow(MingoIcons.Layers, "Built with", "yt-dlp, FFmpeg, youtubedl-android, Jetpack Compose, Material 3, Media3, Haze, Lucide icons and Google Sans Flex.")
            LinkRow(MingoIcons.Github, "Source code", "Coming soon on GitHub") { openUrl(context, GITHUB_URL) }
            LinkRow(MingoIcons.Alert, "Report a problem", "Send feedback by email") {
                sendEmail(context, subject = "Mingo $version — Bug report")
            }
        }

        SectionLabel("Please note")
        Group {
            InfoRow(
                MingoIcons.Info,
                "Download responsibly",
                "Only save content you own or have permission to use, and respect each platform's terms and creators' rights.",
            )
        }

        Text(
            "Made with care by $DEVELOPER",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
        Spacer(Modifier.height(LocalBottomBarPadding.current + 16.dp))
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            MingoIcon(MingoIcons.External, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EngineRow(state: MingoEngine.State, onUpdate: () -> Unit) {
    val status = when (state) {
        is MingoEngine.State.Ready -> "yt-dlp ${state.ytDlpVersion ?: "—"}"
        MingoEngine.State.Initializing -> "Preparing…"
        MingoEngine.State.Updating -> "Updating…"
        is MingoEngine.State.Failed -> "Not available"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(MingoIcons.Cpu)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Download engine", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onUpdate, enabled = state is MingoEngine.State.Ready) { Text("Check now") }
    }
}

private fun sendEmail(context: Context, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:$EMAIL".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, EMAIL, Toast.LENGTH_LONG).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
