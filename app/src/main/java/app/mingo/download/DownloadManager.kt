package app.mingo.download

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.mingo.data.SettingsRepository
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tracks every download and hands the actual work to [DownloadWorker], which keeps running while
 * the app is in the background. Several downloads run side by side, up to the user's limit.
 *
 * Pausing stops the worker but keeps yt-dlp's partial files, so resuming continues where it
 * stopped. Paused downloads are saved to disk so they survive the app being closed.
 */
class DownloadManager(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val pausedFile = File(appContext.filesDir, "paused_downloads.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    /** Newest first. */
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** Tasks whose worker is being stopped because the user paused them, not canceled them. */
    private val pausing = ConcurrentHashMap.newKeySet<String>()

    private val running = MutableStateFlow(0)
    private val slotLock = Mutex()

    init {
        scope.launch(Dispatchers.IO) { restorePaused() }
    }

    fun enqueue(options: DownloadOptions): String {
        val task = DownloadTask(id = UUID.randomUUID().toString(), options = options)
        upsert(task)
        schedule(task)
        return task.id
    }

    fun pause(id: String) {
        val task = get(id)?.takeIf { it.canPause } ?: return
        pausing.add(id)
        YoutubeDL.getInstance().destroyProcessById(id)
        workManager.cancelWorkById(UUID.fromString(task.workId))
        update(id) { it.copy(status = DownloadTask.Status.Paused, speed = null, etaSeconds = -1) }
        DownloadNotifications.cancelProgress(appContext, id)
        savePaused()
    }

    fun resume(id: String) {
        val task = get(id)?.takeIf { it.status == DownloadTask.Status.Paused } ?: return
        val resumed = task.copy(workId = UUID.randomUUID().toString(), status = DownloadTask.Status.Queued)
        upsert(resumed)
        schedule(resumed)
        savePaused()
    }

    fun cancel(id: String) {
        val task = get(id) ?: return
        if (task.status == DownloadTask.Status.Paused) {
            update(id) { it.copy(status = DownloadTask.Status.Canceled) }
            workDir(id).deleteRecursively()
            savePaused()
            return
        }
        YoutubeDL.getInstance().destroyProcessById(id)
        workManager.cancelWorkById(UUID.fromString(task.workId))
        update(id) { if (it.isActive) it.copy(status = DownloadTask.Status.Canceled) else it }
    }

    fun retry(id: String) {
        val task = get(id) ?: return
        remove(id)
        enqueue(task.options)
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.isUnfinished } }
    }

    /** Waits until fewer than the allowed number of downloads are running, then takes a slot. */
    internal suspend fun acquireSlot() {
        while (true) {
            slotLock.withLock {
                if (running.value < settings.settings.value.maxParallelDownloads) {
                    running.update { it + 1 }
                    return
                }
            }
            combine(running, settings.settings) { count, s -> count < s.maxParallelDownloads }.first { it }
        }
    }

    internal fun releaseSlot() {
        running.update { (it - 1).coerceAtLeast(0) }
    }

    /** True once if the worker for [id] was stopped by [pause]; the worker then keeps its files. */
    internal fun consumePausing(id: String): Boolean = pausing.remove(id)

    internal fun workDir(id: String) = File(appContext.cacheDir, "downloads/$id")

    internal fun upsert(task: DownloadTask) {
        _tasks.update { list ->
            if (list.any { it.id == task.id }) list.map { if (it.id == task.id) task else it }
            else listOf(task) + list
        }
    }

    internal fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    internal fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun schedule(task: DownloadTask) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(UUID.fromString(task.workId))
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TASK_ID to task.id,
                    DownloadWorker.KEY_OPTIONS to Json.encodeToString(task.options),
                ),
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueue(request)
    }

    @Serializable
    private data class PausedEntry(val id: String, val options: DownloadOptions, val progress: Float, val createdAt: Long)

    private fun savePaused() {
        val entries = _tasks.value.filter { it.status == DownloadTask.Status.Paused }
            .map { PausedEntry(it.id, it.options, it.progress, it.createdAt) }
        scope.launch(Dispatchers.IO) {
            runCatching { pausedFile.writeText(json.encodeToString(entries)) }
                .onFailure { Log.w(TAG, "Could not save paused downloads", it) }
        }
    }

    private fun restorePaused() {
        if (!pausedFile.exists()) return
        val entries = runCatching { json.decodeFromString<List<PausedEntry>>(pausedFile.readText()) }.getOrDefault(emptyList())
        entries.forEach { e ->
            if (get(e.id) == null) {
                upsert(
                    DownloadTask(
                        id = e.id,
                        options = e.options,
                        status = DownloadTask.Status.Paused,
                        progress = e.progress,
                        createdAt = e.createdAt,
                    ),
                )
            }
        }
        _tasks.update { list -> list.sortedByDescending { it.createdAt } }
    }

    companion object {
        const val WORK_TAG = "download"
        private const val TAG = "DownloadManager"
    }
}
