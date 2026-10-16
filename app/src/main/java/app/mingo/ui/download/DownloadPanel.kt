package app.mingo.ui.download

import androidx.compose.foundation.text.selection.SelectionContainer
import app.mingo.engine.MingoError
import app.mingo.engine.ErrorKind
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mingo.R
import app.mingo.data.AudioBitrate
import app.mingo.data.AudioFormat
import app.mingo.data.DownloadType
import app.mingo.data.VideoContainer
import app.mingo.engine.MediaInfo
import app.mingo.engine.MingoEngine
import app.mingo.ui.components.ChoiceChips
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.ScreenTitle
import app.mingo.ui.components.Segment
import app.mingo.ui.components.SegmentedToggle
import app.mingo.ui.components.ToggleRow
import app.mingo.ui.formatBytes
import app.mingo.ui.formatDuration
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Friendly explanation of a failure, with yt-dlp's raw message one tap away. */
@Composable
fun ErrorCard(error: MingoError) {
    var showDetails by remember { mutableStateOf(false) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tint = MaterialTheme.colorScheme.errorContainer,
        tintAlpha = 0.85f,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MingoIcon(error.kind.icon, size = 22.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(error.kind.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        stringResource(error.kind.hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
            }
            if (error.details.isNotBlank()) {
                TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        stringResource(if (showDetails) R.string.action_hide_details else R.string.action_show_details),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                AnimatedVisibility(visible = showDetails) {
                    SelectionContainer {
                        Text(
                            error.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

private val ErrorKind.icon: Int
    get() = when (this) {
        ErrorKind.Offline -> MingoIcons.WifiOff
        ErrorKind.Network -> MingoIcons.CloudOff
        ErrorKind.Unsupported -> MingoIcons.LinkOff
        ErrorKind.Private, ErrorKind.AgeRestricted -> MingoIcons.Lock
        ErrorKind.Unavailable -> MingoIcons.VideoOff
        ErrorKind.GeoBlocked -> MingoIcons.MapOff
        ErrorKind.RateLimited -> MingoIcons.Timer
        ErrorKind.Format -> MingoIcons.Alert
        ErrorKind.Storage -> MingoIcons.Drive
        ErrorKind.Live -> MingoIcons.Radio
        ErrorKind.Engine -> MingoIcons.ServerCrash
        ErrorKind.Unknown -> MingoIcons.Alert
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewCard(info: MediaInfo) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Darken the bottom so the badges stay readable on bright thumbnails.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
                )
                info.extractor?.let {
                    Badge(it, Modifier.align(Alignment.TopStart).padding(12.dp))
                }
                if (info.durationSeconds > 0) {
                    Badge(formatDuration(info.durationSeconds), Modifier.align(Alignment.BottomEnd).padding(12.dp))
                }
            }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 8.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                info.uploader?.let {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MingoIcon(MingoIcons.User, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun DownloadOptionsPanel(
    info: MediaInfo,
    selection: Selection,
    onChange: ((Selection) -> Selection) -> Unit,
    modifier: Modifier = Modifier,
    /** Hide the extras behind a "More options" row, for space-constrained places like the share sheet. */
    collapseExtras: Boolean = false,
) {
    val s = selection
    val bestLabel = stringResource(R.string.quality_best)
    val losslessLabel = stringResource(R.string.format_lossless)
    var extrasOpen by remember { mutableStateOf(!collapseExtras) }
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 16.dp)) {
            if (info.hasVideo) {
                SegmentedToggle(
                    segments = listOf(
                        Segment(DownloadType.Video, stringResource(R.string.type_video), MingoIcons.Video),
                        Segment(DownloadType.Audio, stringResource(R.string.type_audio), MingoIcons.Audio),
                    ),
                    selected = s.type,
                    onSelect = { type -> onChange { it.copy(type = type) } },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            AnimatedContent(targetState = s.type, label = "options") { type ->
                Column {
                    when (type) {
                        DownloadType.Video -> {
                            OptionLabel(stringResource(R.string.option_quality))
                            ChoiceChips(
                                options = info.qualityOptions,
                                selected = s.height,
                                onSelect = { h -> onChange { it.copy(height = h) } },
                                label = { qualityLabel(it, bestLabel) },
                                detail = { q ->
                                    val h = q ?: info.videoHeights.firstOrNull()
                                    h?.let { info.sizeByHeight[it] }?.let { "≈ ${formatBytes(it)}" }
                                },
                            )
                            OptionLabel(stringResource(R.string.option_format))
                            ChoiceChips(
                                options = VideoContainer.entries,
                                selected = s.container,
                                onSelect = { c -> onChange { it.copy(container = c) } },
                                label = { it.name },
                            )
                        }
                        DownloadType.Audio -> {
                            OptionLabel(stringResource(R.string.option_format))
                            ChoiceChips(
                                options = AudioFormat.entries,
                                selected = s.audioFormat,
                                onSelect = { f -> onChange { it.copy(audioFormat = f) } },
                                label = { it.name },
                                detail = { if (it.lossless) losslessLabel else null },
                            )
                            if (!s.audioFormat.lossless) {
                                OptionLabel(stringResource(R.string.option_bitrate))
                                ChoiceChips(
                                    options = AudioBitrate.entries,
                                    selected = s.audioBitrate,
                                    onSelect = { b -> onChange { it.copy(audioBitrate = b) } },
                                    label = { it.label },
                                    detail = { b ->
                                        info.durationSeconds.takeIf { it > 0 }
                                            ?.let { "≈ ${formatBytes(it.toLong() * b.kbps * 1000 / 8)}" }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (collapseExtras) {
                MoreOptionsRow(open = extrasOpen, onToggle = { extrasOpen = !extrasOpen })
            } else {
                OptionLabel(stringResource(R.string.option_extras))
            }
            AnimatedVisibility(visible = extrasOpen) {
            Column {
            ToggleRow(
                icon = MingoIcons.Image,
                title = stringResource(if (s.type == DownloadType.Audio) R.string.extra_cover else R.string.extra_thumbnail),
                checked = s.embedThumbnail,
                onCheckedChange = { v -> onChange { it.copy(embedThumbnail = v) } },
            )
            ToggleRow(
                icon = MingoIcons.Layers,
                title = stringResource(R.string.extra_metadata),
                checked = s.embedMetadata,
                onCheckedChange = { v -> onChange { it.copy(embedMetadata = v) } },
            )
            if (s.type == DownloadType.Video) {
                ToggleRow(
                    icon = MingoIcons.Clock,
                    title = stringResource(R.string.extra_chapters),
                    checked = s.embedChapters,
                    onCheckedChange = { v -> onChange { it.copy(embedChapters = v) } },
                )
                ToggleRow(
                    icon = MingoIcons.Captions,
                    title = stringResource(R.string.extra_subtitles),
                    checked = s.embedSubtitles,
                    onCheckedChange = { v -> onChange { it.copy(embedSubtitles = v) } },
                )
            }
            if (info.extractor.equals("Youtube", ignoreCase = true)) {
                ToggleRow(
                    icon = MingoIcons.Shield,
                    title = stringResource(R.string.extra_sponsorblock),
                    description = stringResource(R.string.extra_sponsorblock_desc),
                    checked = s.sponsorBlock,
                    onCheckedChange = { v -> onChange { it.copy(sponsorBlock = v) } },
                )
            }
            }
            }
        }
    }
}

@Composable
private fun MoreOptionsRow(open: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MingoIcon(MingoIcons.Sparkles, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.option_more),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MingoIcon(
            MingoIcons.ChevronRight,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
fun DownloadButton(info: MediaInfo, selection: Selection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = when (selection.type) {
        DownloadType.Video -> {
            val size = (selection.height ?: info.videoHeights.firstOrNull())?.let { info.sizeByHeight[it] }
            listOfNotNull(qualityLabel(selection.height, stringResource(R.string.quality_best)), selection.container.name, size?.let { "≈ ${formatBytes(it)}" })
        }
        DownloadType.Audio -> listOfNotNull(
            selection.audioFormat.name,
            if (selection.audioFormat.lossless) null else selection.audioBitrate.label,
        )
    }.joinToString(" · ")
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        MingoIcon(MingoIcons.Downloads, size = 24.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.action_download), style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.labelMedium)
        }
    }
}
