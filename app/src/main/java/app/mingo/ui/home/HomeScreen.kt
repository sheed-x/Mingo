package app.mingo.ui.home

import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.mingo.ui.download.DownloadButton
import app.mingo.ui.download.DownloadOptionsPanel
import app.mingo.ui.download.ErrorCard
import app.mingo.ui.download.PreviewCard
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenDownloads: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val focusManager = LocalFocusManager.current
    val queuedText = stringResource(R.string.home_queued)
    val viewText = stringResource(R.string.action_view)

    // Android only lets the focused app read the clipboard, so check whenever we regain focus.
    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            val text = runCatching {
                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            }.getOrNull()
            viewModel.onClipboardText(text)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Queued -> {
                    val result = snackbar.showSnackbar(queuedText.format(event.title), actionLabel = viewText)
                    if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
                }
            }
        }
    }

    // Notifications are optional (downloads run without them); storage access is required on Android 9 and older.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.download()
    }
    val startDownload: () -> Unit = {
        focusManager.clearFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.has(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !context.has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.download()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenTitle(
                text = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.home_subtitle),
            )
            EngineChip(engineState, onRetry = viewModel::retryEngine, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))

            LinkInput(
                url = state.url,
                onUrlChange = viewModel::onUrlChange,
                onSubmit = { focusManager.clearFocus(); viewModel.fetch() },
                onPaste = {
                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        viewModel.onUrlChange(text.trim())
                        viewModel.fetch()
                    }
                },
                onClear = viewModel::clear,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            AnimatedVisibility(
                visible = state.clipboardLink != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ClipboardSuggestion(
                    link = state.clipboardLink.orEmpty(),
                    onUse = viewModel::useClipboardLink,
                    onDismiss = viewModel::dismissClipboardLink,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = when {
                    state.isFetching -> ContentMode.Loading
                    state.info != null -> ContentMode.Preview
                    state.error != null -> ContentMode.Error
                    else -> ContentMode.Idle
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home-content",
            ) { mode ->
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (mode) {
                        ContentMode.Idle -> {
                            AnalyzeButton(
                                enabled = state.url.isNotBlank(),
                                onClick = { focusManager.clearFocus(); viewModel.fetch() },
                            )
                            SupportedSites()
                        }
                        ContentMode.Loading -> AnalyzingCard()
                        ContentMode.Error -> {
                            state.error?.let { ErrorCard(it) }
                            AnalyzeButton(enabled = state.url.isNotBlank(), onClick = viewModel::fetch, retry = true)
                        }
                        ContentMode.Preview -> state.info?.let { info ->
                            PreviewCard(info)
                            DownloadOptionsPanel(
                                info = info,
                                selection = state.selection,
                                onChange = viewModel::updateSelection,
                            )
                            DownloadButton(info = info, selection = state.selection, onClick = startDownload)
                        }
                    }
                }
            }
            Spacer(Modifier.height(LocalBottomBarPadding.current + 16.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalBottomBarPadding.current),
        )
    }
}

private enum class ContentMode { Idle, Loading, Preview, Error }

private fun android.content.Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EngineChip(state: MingoEngine.State, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                MingoEngine.State.Initializing, MingoEngine.State.Updating -> {
                    LoadingIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (state == MingoEngine.State.Updating) R.string.engine_updating else R.string.engine_preparing,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                is MingoEngine.State.Ready -> {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOfNotNull(stringResource(R.string.engine_ready), state.ytDlpVersion?.let { "yt-dlp $it" })
                            .joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is MingoEngine.State.Failed -> {
                    MingoIcon(MingoIcons.Alert, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.engine_failed), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun LinkInput(
    url: String,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: suspend () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.home_link_hint)) },
                leadingIcon = { MingoIcon(MingoIcons.Link, size = 22.dp) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            )
            if (url.isEmpty()) {
                FilledTonalIconButton(onClick = { scope.launch { onPaste() } }) {
                    MingoIcon(MingoIcons.Paste, contentDescription = stringResource(R.string.action_paste), size = 20.dp)
                }
            } else {
                FilledTonalIconButton(onClick = onClear) {
                    MingoIcon(MingoIcons.Close, contentDescription = stringResource(R.string.action_clear), size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun ClipboardSuggestion(link: String, onUse: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tint = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MingoIcon(MingoIcons.Sparkles, size = 20.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_clipboard_found),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
            Button(onClick = onUse) { Text(stringResource(R.string.action_use)) }
        }
    }
}

@Composable
private fun AnalyzeButton(enabled: Boolean, onClick: () -> Unit, retry: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
    ) {
        MingoIcon(if (retry) MingoIcons.Retry else MingoIcons.Wand, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (retry) R.string.action_retry else R.string.home_fetch),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private data class Site(val name: String, @androidx.annotation.DrawableRes val icon: Int)

// Ordered roughly by how often people download from each platform.
private val supportedSites = listOf(
    Site("YouTube", MingoIcons.YouTube),
    Site("Instagram", MingoIcons.Instagram),
    Site("TikTok", MingoIcons.TikTok),
    Site("Facebook", MingoIcons.Facebook),
    Site("X", MingoIcons.X),
    Site("Snapchat", MingoIcons.Snapchat),
    Site("Reddit", MingoIcons.Reddit),
    Site("Twitch", MingoIcons.Twitch),
    Site("SoundCloud", MingoIcons.SoundCloud),
    Site("Vimeo", MingoIcons.Vimeo),
    Site("Pinterest", MingoIcons.Pinterest),
    Site("Dailymotion", MingoIcons.Dailymotion),
)

@Composable
private fun SupportedSites() {
    // Badges cycle through the theme's own tonal containers instead of brand colors,
    // so the grid always matches the rest of the app (and the wallpaper, with dynamic color).
    val tones = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.home_works_with),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        SiteRows(supportedSites.take(3), firstRow = 0, tones = tones)
        // The rest stay folded away until asked for, keeping the home screen calm.
        var expanded by rememberSaveable { mutableStateOf(false) }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SiteRows(supportedSites.drop(3), firstRow = 1, tones = tones)
            }
        }
        val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sites-chevron")
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (expanded) stringResource(R.string.home_show_less)
                else stringResource(R.string.home_show_all, supportedSites.size),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(6.dp))
            MingoIcon(MingoIcons.ChevronDown, size = 16.dp, modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
private fun SiteRows(sites: List<Site>, firstRow: Int, tones: List<Pair<Color, Color>>) {
    sites.chunked(3).forEachIndexed { index, rowSites ->
        val row = firstRow + index
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowSites.forEachIndexed { column, site ->
                val (container, content) = tones[(row + column) % tones.size]
                GlassSurface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(container),
                            contentAlignment = Alignment.Center,
                        ) {
                            MingoIcon(site.icon, size = 22.dp, tint = content)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(site.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            // Keep tiles the same width when the last row is not full.
            repeat(3 - rowSites.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnalyzingCard() {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_analyzing), style = MaterialTheme.typography.titleMedium)
        }
    }
}
