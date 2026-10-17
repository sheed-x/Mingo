package app.mingo.download

import app.mingo.engine.MingoError

data class DownloadTask(
    val id: String,
    val options: DownloadOptions,
    /** The WorkManager request currently running this task; a new one is created on every resume. */
    val workId: String = id,
    val status: Status = Status.Queued,
    /** 0..1 */
    val progress: Float = 0f,
    val etaSeconds: Long = -1,
    val speed: String? = null,
    val totalSize: String? = null,
    val outputUri: String? = null,
    val mimeType: String? = null,
    val error: MingoError? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    enum class Status { Queued, Running, Processing, Paused, Completed, Failed, Canceled }

    /** Work is scheduled or running right now. */
    val isActive: Boolean get() = status == Status.Queued || status == Status.Running || status == Status.Processing

    /** Not done yet: active, or paused and waiting to be resumed. */
    val isUnfinished: Boolean get() = isActive || status == Status.Paused

    val canPause: Boolean get() = status == Status.Queued || status == Status.Running
}
