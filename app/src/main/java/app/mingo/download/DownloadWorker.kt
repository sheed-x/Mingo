package app.mingo.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.mingo.MingoApp
import app.mingo.data.LibraryItem
import app.mingo.engine.ErrorKind
import app.mingo.engine.MingoError
import app.mingo.engine.isOnline
import app.mingo.engine.toMingoError
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val app = context.applicationContext as MingoApp
    private val manager = app.downloadManager
    private val taskId = inputData.getString(KEY_TASK_ID)!!
    private val options = Json.decodeFromString<DownloadOptions>(inputData.getString(KEY_OPTIONS)!!)

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(manager.get(taskId))

    override suspend fun doWork(): Result {
        // After a process restart WorkManager re-runs pending work, but the in-memory list is empty.
        if (manager.get(taskId) == null) manager.upsert(DownloadTask(id = taskId, options = options))
        // Starting a foreground service can be refused when the app is in the background;
        // the download still runs, just without the guarantee.
        runCatching { setForeground(foregroundInfo(manager.get(taskId))) }
            .onFailure { Log.w(TAG, "Could not promote download to foreground", it) }

        if (!app.engine.awaitReady()) return fail(MingoError(ErrorKind.Engine))
        if (!applicationContext.isOnline()) return fail(MingoError(ErrorKind.Offline))

        // Wait for a free slot; the user decides how many downloads run side by side.
        try {
            manager.acquireSlot()
        } catch (e: CancellationException) {
            if (!manager.consumePausing(taskId)) canceled()
            throw e
        }

        // Kept between runs: when a paused download resumes, yt-dlp continues its partial files.
        val workDir = manager.workDir(taskId).apply { mkdirs() }
        var paused = false
        // The yt-dlp call blocks, so cancellation has to kill its process explicitly.
        val cancelHook = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) YoutubeDL.getInstance().destroyProcessById(taskId)
        }

        return try {
            manager.update(taskId) { it.copy(status = DownloadTask.Status.Running) }
            runDownload(workDir)

            manager.update(taskId) { it.copy(status = DownloadTask.Status.Processing, progress = 1f) }
            DownloadNotifications.notifyProgress(applicationContext, taskId, options.title, manager.get(taskId))
            val output = pickOutput(workDir) ?: return fail(MingoError(ErrorKind.Unknown, "Download finished but no file was produced"))
            val saved = MediaSaver.save(applicationContext, output)
            app.library.add(
                LibraryItem(
                    id = taskId,
                    options = options,
                    uri = saved.uri.toString(),
                    mimeType = saved.mimeType,
                    fileName = output.name,
                    sizeBytes = output.length(),
                    createdAt = System.currentTimeMillis(),
                    path = saved.path,
                ),
            )

            manager.update(taskId) {
                it.copy(
                    status = DownloadTask.Status.Completed,
                    outputUri = saved.uri.toString(),
                    mimeType = saved.mimeType,
                )
            }
            DownloadNotifications.cancelProgress(applicationContext, taskId)
            DownloadNotifications.notifyDone(applicationContext, taskId, options.title, saved.uri, saved.mimeType)
            Result.success()
        } catch (e: YoutubeDL.CanceledException) {
            paused = manager.consumePausing(taskId)
            if (paused) pausedResult() else canceled()
        } catch (e: CancellationException) {
            paused = manager.consumePausing(taskId)
            if (paused) pausedResult() else canceled()
            throw e
        } catch (e: Exception) {
            when {
                isStopped -> {
                    paused = manager.consumePausing(taskId)
                    if (paused) pausedResult() else canceled()
                }
                else -> fail(applicationContext.toMingoError(e))
            }
        } finally {
            manager.releaseSlot()
            cancelHook.dispose()
            if (!paused) workDir.deleteRecursively()
        }
    }

    private suspend fun runDownload(workDir: File) = withContext(Dispatchers.IO) {
        val attempts = options.attempts()
        attempts.forEachIndexed { index, extra ->
            try {
                execute(options.copy(extraArgs = extra + options.extraArgs), workDir)
                return@withContext
            } catch (e: YoutubeDLException) {
                if (index == attempts.lastIndex || isStopped) throw e
                Log.i(TAG, "Attempt ${index + 1} failed, trying the next source: ${e.message?.lines()?.lastOrNull()}")
                workDir.listFiles()?.forEach { it.deleteRecursively() }
                manager.update(taskId) { it.copy(progress = 0f, speed = null, etaSeconds = -1) }
            }
        }
    }

    private fun execute(attempt: DownloadOptions, workDir: File) {
        var lastNotified = 0L
        val request = attempt.toRequest(workDir)
        Log.d(TAG, "yt-dlp ${request.buildCommand().joinToString(" ")}")
        YoutubeDL.getInstance().execute(request, taskId) { progress, eta, line ->
            Log.v(TAG, line)
            manager.update(taskId) {
                if (!it.isActive) return@update it
                it.copy(
                    status = DownloadTask.Status.Running,
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    etaSeconds = eta,
                    speed = SPEED.find(line)?.groupValues?.get(1) ?: it.speed,
                    totalSize = SIZE.find(line)?.groupValues?.get(1) ?: it.totalSize,
                )
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotified > 750) {
                lastNotified = now
                DownloadNotifications.notifyProgress(applicationContext, taskId, options.title, manager.get(taskId))
            }
        }
    }

    /** yt-dlp may leave thumbnails or partial files next to the result; the largest file is the media. */
    private fun pickOutput(dir: File): File? = dir.walkTopDown()
        .filter { it.isFile && it.extension !in setOf("part", "ytdl", "temp") }
        .maxByOrNull { it.length() }

    private fun fail(error: MingoError): Result {
        Log.w(TAG, "Download failed (${error.kind}): ${error.details}")
        manager.update(taskId) { it.copy(status = DownloadTask.Status.Failed, error = error) }
        DownloadNotifications.cancelProgress(applicationContext, taskId)
        DownloadNotifications.notifyFailed(applicationContext, taskId, options.title, applicationContext.getString(error.kind.title))
        return Result.failure()
    }

    /** The manager already marked the task paused; just clear the progress notification. */
    private fun pausedResult(): Result {
        DownloadNotifications.cancelProgress(applicationContext, taskId)
        return Result.failure()
    }

    private fun canceled(): Result {
        manager.update(taskId) { it.copy(status = DownloadTask.Status.Canceled) }
        DownloadNotifications.cancelProgress(applicationContext, taskId)
        return Result.failure()
    }

    private fun foregroundInfo(task: DownloadTask?): ForegroundInfo {
        val notification = DownloadNotifications.progress(applicationContext, taskId, options.title, task)
        val id = DownloadNotifications.progressId(taskId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_OPTIONS = "options"
        private const val TAG = "DownloadWorker"
        private val SPEED = Regex("""at\s+(\S+/s)""")
        private val SIZE = Regex("""of\s+~?\s*(\d[\d.]*\s*\w+)""")
    }
}
