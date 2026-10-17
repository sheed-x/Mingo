package app.mingo.ui.settings

import app.mingo.update.UpdateState
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mingo.MingoApp
import app.mingo.R
import app.mingo.data.AppSettings
import app.mingo.data.AudioBitrate
import app.mingo.data.AudioFormat
import app.mingo.data.DownloadType
import app.mingo.data.ThemeMode
import app.mingo.data.VideoContainer
import app.mingo.data.VideoQuality
import app.mingo.engine.MingoEngine
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.IconBadge
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.ScreenTitle
import app.mingo.ui.components.SectionLabel
import app.mingo.ui.components.Segment
import app.mingo.ui.components.SegmentedToggle
import app.mingo.ui.components.ToggleRow
import app.mingo.ui.components.ValueRow
import app.mingo.ui.icons.MingoIcons
import kotlinx.coroutines.launch

/** A picker currently shown in the bottom sheet. */
private class Choice<T>(
    val title: String,
    val options: List<T>,
    val selected: T,
    val label: (T) -> String,
    val onSelect: (T) -> Unit,
)

@Composable
fun SettingsScreen(onOpenAbout: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MingoApp
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val engineState by app.engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val update: ((AppSettings) -> AppSettings) -> Unit = { transform -> scope.launch { app.settings.update(transform) } }
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    val context = LocalContext.current
    val appVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))

        SectionLabel(stringResource(R.string.settings_appearance))
        Group {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                SegmentedToggle(
                    segments = listOf(
                        Segment(ThemeMode.System, stringResource(R.string.theme_system), MingoIcons.SunMoon),
                        Segment(ThemeMode.Light, stringResource(R.string.theme_light)),
                        Segment(ThemeMode.Dark, stringResource(R.string.theme_dark), MingoIcons.Moon),
                    ),
                    selected = settings.themeMode,
                    onSelect = { mode -> update { it.copy(themeMode = mode) } },
                )
            }
            val languageTitle = stringResource(R.string.settings_language)
            val systemLabel = stringResource(R.string.lang_system)
            // Language names are shown in their own script so they are recognizable in any UI language.
            val languages = listOf("" to systemLabel) + AppLanguages
            val appTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            // Match the exact tag first (zh-CN), then just the language (Android may report "in" for Indonesian).
            val currentTag = languages.firstOrNull { it.first.equals(appTags, ignoreCase = true) }?.first
                ?: languages.firstOrNull { it.first == appTags.substringBefore('-').replace("in", "id") }?.first
                ?: ""
            ValueRow(
                icon = MingoIcons.Languages,
                title = languageTitle,
                value = languages.firstOrNull { it.first == currentTag }?.second ?: systemLabel,
                onClick = {
                    choice = Choice(languageTitle, languages.map { it.first }, currentTag, { tag ->
                        languages.first { it.first == tag }.second
                    }) { tag ->
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                        )
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ToggleRow(
                    icon = MingoIcons.Palette,
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> update { it.copy(dynamicColor = v) } },
                )
            }
            ToggleRow(
                icon = MingoIcons.Contrast,
                title = stringResource(R.string.settings_amoled),
                description = stringResource(R.string.settings_amoled_desc),
                checked = settings.amoledBlack,
                onCheckedChange = { v -> update { it.copy(amoledBlack = v) } },
            )
        }

        SectionLabel(stringResource(R.string.settings_smart))
        Group {
            ToggleRow(
                icon = MingoIcons.Paste,
                title = stringResource(R.string.settings_auto_paste),
                description = stringResource(R.string.settings_auto_paste_desc),
                checked = settings.autoPasteLinks,
                onCheckedChange = { v -> update { it.copy(autoPasteLinks = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Wand,
                title = stringResource(R.string.settings_auto_analyze),
                description = stringResource(R.string.settings_auto_analyze_desc),
                checked = settings.autoAnalyze,
                onCheckedChange = { v -> update { it.copy(autoAnalyze = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Zap,
                title = stringResource(R.string.settings_instant_share),
                description = stringResource(R.string.settings_instant_share_desc),
                checked = settings.instantShareDownload,
                onCheckedChange = { v -> update { it.copy(instantShareDownload = v) } },
            )
        }

        SectionLabel(stringResource(R.string.settings_defaults))
        val videoLabel = stringResource(R.string.type_video)
        val audioLabel = stringResource(R.string.type_audio)
        Group {
            ValueRow(
                icon = MingoIcons.Zap,
                title = stringResource(R.string.settings_default_type),
                description = stringResource(R.string.settings_default_type_desc),
                value = if (settings.defaultType == DownloadType.Video) videoLabel else audioLabel,
                onClick = {
                    choice = Choice(
                        videoLabel + " / " + audioLabel, DownloadType.entries, settings.defaultType,
                        { if (it == DownloadType.Video) videoLabel else audioLabel },
                    ) { v -> update { it.copy(defaultType = v) } }
                },
            )
            val qualityTitle = stringResource(R.string.settings_video_quality)
            ValueRow(
                icon = MingoIcons.Monitor,
                title = qualityTitle,
                description = stringResource(R.string.settings_video_quality_desc),
                value = settings.videoQuality.label,
                onClick = {
                    choice = Choice(qualityTitle, VideoQuality.entries, settings.videoQuality, { it.label }) { v ->
                        update { it.copy(videoQuality = v) }
                    }
                },
            )
            val containerTitle = stringResource(R.string.settings_video_format)
            ValueRow(
                icon = MingoIcons.Video,
                title = containerTitle,
                description = stringResource(R.string.settings_video_format_desc),
                value = settings.videoContainer.name,
                onClick = {
                    choice = Choice(containerTitle, VideoContainer.entries, settings.videoContainer, { it.name }) { v ->
                        update { it.copy(videoContainer = v) }
                    }
                },
            )
            val audioTitle = stringResource(R.string.settings_audio_format)
            ValueRow(
                icon = MingoIcons.Headphones,
                title = audioTitle,
                description = stringResource(R.string.settings_audio_format_desc),
                value = settings.audioFormat.name,
                onClick = {
                    choice = Choice(audioTitle, AudioFormat.entries, settings.audioFormat, { it.name }) { v ->
                        update { it.copy(audioFormat = v) }
                    }
                },
            )
            val bitrateTitle = stringResource(R.string.settings_audio_bitrate)
            ValueRow(
                icon = MingoIcons.Gauge,
                title = bitrateTitle,
                description = stringResource(R.string.settings_audio_bitrate_desc),
                value = settings.audioBitrate.label,
                onClick = {
                    choice = Choice(bitrateTitle, AudioBitrate.entries, settings.audioBitrate, { it.label }) { v ->
                        update { it.copy(audioBitrate = v) }
                    }
                },
            )
            val parallelTitle = stringResource(R.string.settings_parallel)
            ValueRow(
                icon = MingoIcons.Layers,
                title = parallelTitle,
                description = stringResource(R.string.settings_parallel_desc),
                value = "${settings.maxParallelDownloads}",
                onClick = {
                    choice = Choice(parallelTitle, (1..5).toList(), settings.maxParallelDownloads, { "$it" }) { v ->
                        update { it.copy(maxParallelDownloads = v) }
                    }
                },
            )
        }

        SectionLabel(stringResource(R.string.settings_processing))
        Group {
            ToggleRow(
                icon = MingoIcons.Image,
                title = stringResource(R.string.extra_thumbnail),
                description = stringResource(R.string.extra_thumbnail_desc),
                checked = settings.embedThumbnail,
                onCheckedChange = { v -> update { it.copy(embedThumbnail = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Layers,
                title = stringResource(R.string.extra_metadata),
                description = stringResource(R.string.extra_metadata_desc),
                checked = settings.embedMetadata,
                onCheckedChange = { v -> update { it.copy(embedMetadata = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Clock,
                title = stringResource(R.string.extra_chapters),
                description = stringResource(R.string.extra_chapters_desc),
                checked = settings.embedChapters,
                onCheckedChange = { v -> update { it.copy(embedChapters = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Captions,
                title = stringResource(R.string.extra_subtitles),
                description = stringResource(R.string.extra_subtitles_desc),
                checked = settings.embedSubtitles,
                onCheckedChange = { v -> update { it.copy(embedSubtitles = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Shield,
                title = stringResource(R.string.extra_sponsorblock),
                description = stringResource(R.string.extra_sponsorblock_desc),
                checked = settings.sponsorBlock,
                onCheckedChange = { v -> update { it.copy(sponsorBlock = v) } },
            )
        }

        SectionLabel(stringResource(R.string.settings_updates))
        val updateState by app.updates.state.collectAsStateWithLifecycle()
        val upToDate = stringResource(R.string.update_none)
        LaunchedEffect(updateState) {
            if (updateState == UpdateState.UpToDate) Toast.makeText(context, upToDate, Toast.LENGTH_SHORT).show()
        }
        Group {
            ToggleRow(
                icon = MingoIcons.Refresh,
                title = stringResource(R.string.settings_auto_update),
                description = stringResource(R.string.settings_auto_update_desc),
                checked = settings.autoCheckUpdates,
                onCheckedChange = { v -> update { it.copy(autoCheckUpdates = v) } },
            )
            ValueRow(
                icon = MingoIcons.Download,
                title = stringResource(R.string.settings_check_updates),
                description = stringResource(R.string.settings_check_updates_desc, app.updates.currentVersion),
                value = if (updateState == UpdateState.Checking) stringResource(R.string.update_checking) else "",
                onClick = { app.updates.check() },
            )
        }

        // The About page is English-only by design, so its entry is too, laid out left-to-right.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SectionLabel("About")
        Group {
            ValueRow(
                icon = MingoIcons.Info,
                title = "About Mingo",
                value = "Version $appVersion",
                description = "Developer, how it works, privacy and credits",
                onClick = onOpenAbout,
            )
        }
        }

        Spacer(Modifier.height(LocalBottomBarPadding.current + 16.dp))
    }

    choice?.let { ChoiceSheet(it) { choice = null } }
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
private fun InfoRow(icon: Int, title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(choice: Choice<T>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                choice.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
            )
            choice.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) {
                            choice.onSelect(option)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == choice.selected, onClick = null, modifier = Modifier.padding(12.dp))
                    Text(choice.label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Translated languages, each named in its own script so it is recognizable from any UI language. */
private val AppLanguages = listOf(
    "en" to "English",
    "ar" to "العربية",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt" to "Português",
    "ru" to "Русский",
    "zh-CN" to "简体中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "tr" to "Türkçe",
    "id" to "Bahasa Indonesia",
    "hi" to "हिन्दी",
    "it" to "Italiano",
)
