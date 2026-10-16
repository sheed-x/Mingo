package app.mingo.download

import app.mingo.engine.MingoError

data class DownloadTask(
    val id: String,
    val options: DownloadOptions,
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
    enum class Status { Queued, Running, Processing, Completed, Failed, Canceled }

    val isActive: Boolean get() = status == Status.Queued || status == Status.Running || status == Status.Processing
}
