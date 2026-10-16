package app.mingo.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mingo.MingoApp
import app.mingo.R
import app.mingo.data.DownloadType
import app.mingo.download.DownloadManager
import app.mingo.download.DownloadTask
import app.mingo.download.formatEta
import app.mingo.ui.components.EmptyState
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.LocalBottomBarPadding
import app.mingo.ui.components.ScreenTitle
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons
import coil3.compose.AsyncImage

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val manager = (context.applicationContext as MingoApp).downloadManager
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val hasFinished = tasks.any { !it.isActive }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            text = stringResource(R.string.nav_downloads),
            subtitle = tasks.count { it.isActive }.takeIf { it > 0 }
                ?.let { stringResource(R.string.downloads_active_count, it) },
            actions = {
                if (hasFinished) {
                    TextButton(onClick = manager::clearFinished) { Text(stringResource(R.string.action_clear_finished)) }
                }
            },
        )
        if (tasks.isEmpty()) {
            EmptyState(
                icon = MingoIcons.Downloads,
                title = stringResource(R.string.downloads_empty_title),
                message = stringResource(R.string.downloads_empty_message),
                modifier = Modifier.padding(bottom = LocalBottomBarPadding.current),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBottomBarPadding.current + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadCard(task, manager, Modifier.animateItem())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadCard(task: DownloadTask, manager: DownloadManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(
            Modifier
                .clickable(enabled = task.status == DownloadTask.Status.Completed) { open(context, task) }
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(63.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    MingoIcon(
                        if (task.options.type == DownloadType.Audio) MingoIcons.Music else MingoIcons.Video,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AsyncImage(
                        model = task.options.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.options.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusLine(task)
                }
                Spacer(Modifier.width(4.dp))
                TaskAction(task, manager)
            }
            if (task.status == DownloadTask.Status.Running || task.status == DownloadTask.Status.Processing ||
                task.status == DownloadTask.Status.Queued
            ) {
                Spacer(Modifier.height(12.dp))
                if (task.status == DownloadTask.Status.Running && task.progress > 0f) {
                    LinearWavyProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatusLine(task: DownloadTask) {
    val color = when (task.status) {
        DownloadTask.Status.Failed -> MaterialTheme.colorScheme.error
        DownloadTask.Status.Completed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when (task.status) {
        DownloadTask.Status.Queued -> stringResource(R.string.status_queued)
        DownloadTask.Status.Running -> listOfNotNull(
            "${(task.progress * 100).toInt()}%",
            task.totalSize,
            task.speed,
            task.etaSeconds.takeIf { it > 0 }?.let { formatEta(it) },
        ).joinToString(" · ")
        DownloadTask.Status.Processing -> stringResource(R.string.status_processing)
        DownloadTask.Status.Completed -> stringResource(R.string.status_completed, task.options.summary)
        DownloadTask.Status.Failed -> task.error?.let { stringResource(it.kind.title) } ?: stringResource(R.string.status_failed)
        DownloadTask.Status.Canceled -> stringResource(R.string.status_canceled)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun TaskAction(task: DownloadTask, manager: DownloadManager) {
    val context = LocalContext.current
    when (task.status) {
        DownloadTask.Status.Queued, DownloadTask.Status.Running, DownloadTask.Status.Processing ->
            FilledTonalIconButton(onClick = { manager.cancel(task.id) }) {
                MingoIcon(MingoIcons.Stop, contentDescription = stringResource(R.string.action_cancel), size = 20.dp)
            }
        DownloadTask.Status.Completed -> Row {
            IconButton(onClick = { share(context, task) }) {
                MingoIcon(MingoIcons.Share, contentDescription = stringResource(R.string.action_share), size = 20.dp)
            }
            FilledTonalIconButton(onClick = { open(context, task) }) {
                MingoIcon(MingoIcons.Open, contentDescription = stringResource(R.string.action_open), size = 20.dp)
            }
        }
        DownloadTask.Status.Failed, DownloadTask.Status.Canceled -> Row {
            IconButton(onClick = { manager.remove(task.id) }) {
                MingoIcon(MingoIcons.Trash, contentDescription = stringResource(R.string.action_remove), size = 20.dp)
            }
            FilledTonalIconButton(onClick = { manager.retry(task.id) }) {
                MingoIcon(MingoIcons.Retry, contentDescription = stringResource(R.string.action_retry), size = 20.dp)
            }
        }
    }
}

private fun open(context: Context, task: DownloadTask) {
    val uri = task.outputUri?.toUri() ?: return
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, task.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, task: DownloadTask) {
    val uri = task.outputUri?.toUri() ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(task.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
