package app.mingo.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Tracks every download of the current session and hands the actual work to [DownloadWorker],
 * which keeps running while the app is in the background.
 */
class DownloadManager(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    /** Newest first. */
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun enqueue(options: DownloadOptions): String {
        val id = UUID.randomUUID()
        upsert(DownloadTask(id = id.toString(), options = options))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(id)
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_TASK_ID to id.toString(),
                    DownloadWorker.KEY_OPTIONS to Json.encodeToString(options),
                ),
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueue(request)
        return id.toString()
    }

    fun cancel(id: String) {
        YoutubeDL.getInstance().destroyProcessById(id)
        workManager.cancelWorkById(UUID.fromString(id))
        update(id) { if (it.isActive) it.copy(status = DownloadTask.Status.Canceled) else it }
    }

    fun retry(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        remove(id)
        enqueue(task.options)
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.isActive } }
    }

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

    companion object {
        const val WORK_TAG = "download"
    }
}
