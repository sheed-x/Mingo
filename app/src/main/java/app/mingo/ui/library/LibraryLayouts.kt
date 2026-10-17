package app.mingo.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mingo.R
import app.mingo.data.LibraryGroup
import app.mingo.data.LibraryItem
import app.mingo.data.LibraryView
import app.mingo.ui.components.ChoiceChips
import app.mingo.ui.components.GlassSurface
import app.mingo.ui.components.Segment
import app.mingo.ui.components.SegmentedToggle
import app.mingo.ui.formatBytes
import app.mingo.ui.formatDuration
import app.mingo.ui.icons.MingoIcon
import app.mingo.ui.icons.MingoIcons
import coil3.compose.AsyncImage
import java.util.Calendar

/** What a section of the library is about; resolved to text by [groupLabel]. */
sealed interface GroupTitle {
    data class Platform(val name: String) : GroupTitle
    data object Today : GroupTitle
    data object ThisWeek : GroupTitle
    data object Earlier : GroupTitle
}

data class ItemGroup(val title: GroupTitle?, val items: List<LibraryItem>)

/** Splits the (already sorted and filtered) items into sections, keeping their order inside each. */
fun groupItems(items: List<LibraryItem>, group: LibraryGroup, now: Long = System.currentTimeMillis()): List<ItemGroup> =
    when (group) {
        LibraryGroup.None -> listOf(ItemGroup(null, items))
        LibraryGroup.Platform -> items
            .groupBy { it.options.extractor?.takeIf(String::isNotBlank) ?: "Other" }
            .map { (name, list) -> ItemGroup(GroupTitle.Platform(name), list) }
        LibraryGroup.Date -> {
            val startOfToday = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val startOfWeek = startOfToday - 6L * 24 * 60 * 60 * 1000
            val buckets = items.groupBy {
                when {
                    it.createdAt >= startOfToday -> GroupTitle.Today
                    it.createdAt >= startOfWeek -> GroupTitle.ThisWeek
                    else -> GroupTitle.Earlier
                }
            }
            listOf(GroupTitle.Today, GroupTitle.ThisWeek, GroupTitle.Earlier)
                .mapNotNull { title -> buckets[title]?.let { ItemGroup(title, it) } }
        }
    }

@Composable
fun groupLabel(title: GroupTitle): String = when (title) {
    is GroupTitle.Platform -> title.name
    GroupTitle.Today -> stringResource(R.string.group_today)
    GroupTitle.ThisWeek -> stringResource(R.string.group_week)
    GroupTitle.Earlier -> stringResource(R.string.group_earlier)
}

@Composable
fun GroupHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Small square tile: artwork, a one-line title and nothing else, to fit many on screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactCard(item: LibraryItem, onPlay: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier.combinedClickable(
            onClick = onPlay,
            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMore() },
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            MingoIcon(if (item.isAudio) MingoIcons.Music else MingoIcons.Video, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = item.options.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isAudio) {
                    MingoIcon(MingoIcons.Headphones, size = 12.dp, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                }
                item.options.durationSeconds.takeIf { it > 0 }?.let {
                    Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
    }
}

/** Full-width row with a small thumbnail and all the details, like a file list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRowCard(item: LibraryItem, onPlay: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(
            Modifier
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onMore() },
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MingoIcon(if (item.isAudio) MingoIcons.Music else MingoIcons.Video, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                AsyncImage(
                    model = item.options.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.options.durationSeconds.takeIf { it > 0 }?.let {
                    Text(
                        formatDuration(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isAudio) {
                        MingoIcon(MingoIcons.Headphones, size = 14.dp, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        listOfNotNull(
                            item.options.summary,
                            formatBytes(item.sizeBytes).ifEmpty { null },
                            item.options.extractor,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onMore, modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                MingoIcon(MingoIcons.More, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySheet(
    view: LibraryView,
    group: LibraryGroup,
    onView: (LibraryView) -> Unit,
    onGroup: (LibraryGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.library_display),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
            )
            SegmentedToggle(
                segments = listOf(
                    Segment(LibraryView.Grid, stringResource(R.string.view_grid), MingoIcons.Grid),
                    Segment(LibraryView.Compact, stringResource(R.string.view_compact), MingoIcons.GridSmall),
                    Segment(LibraryView.List, stringResource(R.string.view_list), MingoIcons.ListView),
                ),
                selected = view,
                onSelect = onView,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                stringResource(R.string.library_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 10.dp),
            )
            val labels = mapOf(
                LibraryGroup.None to stringResource(R.string.group_none),
                LibraryGroup.Platform to stringResource(R.string.group_platform),
                LibraryGroup.Date to stringResource(R.string.group_date),
            )
            ChoiceChips(
                options = LibraryGroup.entries,
                selected = group,
                onSelect = onGroup,
                label = { labels.getValue(it) },
            )
        }
    }
}
