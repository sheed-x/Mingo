package app.mingo.ui.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mingo.R
import app.mingo.data.DownloadType
import app.mingo.data.LibraryItem
import app.mingo.player.PlayerActivity
import app.mingo.ui.components.ChoiceChips
import app.mingo.ui.components.EmptyState
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.IconBadge
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.ScreenTitle
import app.mingo.ui.formatBytes
import app.mingo.ui.formatDuration
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<LibraryItem?>(null) }
    var confirmDelete by remember { mutableStateOf<LibraryItem?>(null) }
    var showSort by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarPadding.current + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenTitle(
                text = stringResource(R.string.nav_library),
                subtitle = state.all.takeIf { it.isNotEmpty() }
                    ?.let { stringResource(R.string.library_stats, it.size, formatBytes(state.totalBytes)) },
                startPadding = 8.dp,
            )
        }

        if (state.all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = MingoIcons.Library,
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.library_empty_message),
                    modifier = Modifier.height(480.dp),
                )
            }
            return@LazyVerticalGrid
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchField(state.query, viewModel::onQueryChange)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val all = stringResource(R.string.filter_all)
                    val video = stringResource(R.string.type_video)
                    val audio = stringResource(R.string.type_audio)
                    ChoiceChips(
                        options = listOf(null, DownloadType.Video, DownloadType.Audio),
                        selected = state.filter,
                        onSelect = viewModel::onFilterChange,
                        label = { when (it) { null -> all; DownloadType.Video -> video; DownloadType.Audio -> audio } },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 0.dp,
                    )
                    FilledTonalIconButton(onClick = { showSort = true }) {
                        MingoIcon(MingoIcons.Sort, contentDescription = stringResource(R.string.sort_title), size = 20.dp)
                    }
                }
            }
        }

        if (state.visible.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.library_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 48.dp),
                )
            }
        }

        items(state.visible, key = { it.id }) { item ->
            LibraryCard(
                item = item,
                onPlay = { play(context, item) },
                onMore = { actionsFor = item },
                modifier = Modifier.animateItem(),
            )
        }
    }

    actionsFor?.let { item ->
        ActionsSheet(
            item = item,
            onDismiss = { actionsFor = null },
            onDelete = { confirmDelete = item },
            onRedownload = { viewModel.redownload(item) },
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { MingoIcon(MingoIcons.Trash) },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(item) { }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showSort) {
        val labels = mapOf(
            LibrarySort.Newest to stringResource(R.string.sort_newest),
            LibrarySort.Oldest to stringResource(R.string.sort_oldest),
            LibrarySort.Name to stringResource(R.string.sort_name),
            LibrarySort.Size to stringResource(R.string.sort_size),
        )
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    stringResource(R.string.sort_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 8.dp),
                )
                LibrarySort.entries.forEach { sort ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSortChange(sort); showSort = false }
                            .padding(horizontal = 28.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(labels.getValue(sort), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (sort == state.sort) MingoIcon(MingoIcons.Check, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
        TextField(
            value = query,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { MingoIcon(MingoIcons.Search, size = 20.dp) },
            trailingIcon = if (query.isNotEmpty()) {
                { TextButton(onClick = { onChange("") }) { MingoIcon(MingoIcons.Close, size = 18.dp) } }
            } else null,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(item: LibraryItem, onPlay: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.combinedClickable(
                onClick = onPlay,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMore()
                },
            ),
        ) {
            Box(
                Modifier
                    .padding(6.dp)
                    .fillMaxWidth()
                    .aspectRatio(if (item.isAudio) 1f else 16f / 10f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MingoIcon(
                    if (item.isAudio) MingoIcons.Music else MingoIcons.Video,
                    size = 32.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AsyncImage(
                    model = item.options.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))),
                )
                // Play affordance in the center of the artwork.
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MingoIcon(MingoIcons.Play, size = 18.dp, tint = Color.White)
                }
                item.options.durationSeconds.takeIf { it > 0 }?.let {
                    Text(
                        formatDuration(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (item.isAudio) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(6.dp),
                    ) {
                        MingoIcon(MingoIcons.Headphones, size = 14.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            Row(
                Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(formatBytes(item.sizeBytes).ifEmpty { null }, item.options.extractor).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TextButton(onClick = onMore, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                    MingoIcon(MingoIcons.More, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(
    item: LibraryItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRedownload: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.link_copied)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(96.dp)
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    AsyncImage(
                        model = item.options.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(item.options.summary, formatBytes(item.sizeBytes)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ActionRow(MingoIcons.Play, stringResource(R.string.action_play)) { onDismiss(); play(context, item) }
            ActionRow(MingoIcons.Share, stringResource(R.string.action_share)) { onDismiss(); share(context, item) }
            ActionRow(MingoIcons.External, stringResource(R.string.action_open_with)) { onDismiss(); openWith(context, item) }
            ActionRow(MingoIcons.Copy, stringResource(R.string.action_copy_link)) {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(item.title, item.options.url))) }
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            ActionRow(MingoIcons.Retry, stringResource(R.string.action_redownload)) { onDismiss(); onRedownload() }
            ActionRow(MingoIcons.Trash, stringResource(R.string.action_delete), destructive = true) { onDismiss(); onDelete() }
        }
    }
}

@Composable
private fun ActionRow(icon: Int, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon,
            container = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun play(context: Context, item: LibraryItem) {
    context.startActivity(PlayerActivity.intent(context, item.uri.toUri(), item.title, item.isAudio))
}

private fun share(context: Context, item: LibraryItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mimeType)
        .putExtra(Intent.EXTRA_STREAM, item.uri.toUri())
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

private fun openWith(context: Context, item: LibraryItem) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri.toUri(), item.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
    }
}
