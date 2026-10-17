package app.mingo.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mingo.MainActivity
import app.mingo.R

object DownloadNotifications {

    private const val CHANNEL_PROGRESS = "downloads_progress"
    private const val CHANNEL_DONE = "downloads_done"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    context.getString(R.string.channel_progress),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_DONE,
                    context.getString(R.string.channel_done),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun progressId(taskId: String) = taskId.hashCode()
    private fun doneId(taskId: String) = taskId.hashCode() + 1

    fun progress(context: Context, taskId: String, title: String, task: DownloadTask?): android.app.Notification {
        val percent = ((task?.progress ?: 0f) * 100).toInt()
        val indeterminate = task == null || task.status != DownloadTask.Status.Running || percent <= 0
        val text = when (task?.status) {
            DownloadTask.Status.Processing -> context.getString(R.string.status_processing)
            DownloadTask.Status.Running -> listOfNotNull("$percent%", task.speed, task.etaSeconds.takeIf { it > 0 }?.let(::formatEta))
                .joinToString(" · ")
            else -> context.getString(R.string.status_queued)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_mingo)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
        if (task?.canPause != false) {
            builder.addAction(0, context.getString(R.string.action_pause), DownloadActionReceiver.pauseIntent(context, taskId))
        }
        return builder
            .addAction(0, context.getString(R.string.action_cancel), DownloadActionReceiver.cancelIntent(context, taskId))
            .build()
    }

    fun notifyProgress(context: Context, taskId: String, title: String, task: DownloadTask?) {
        notify(context, progressId(taskId), progress(context, taskId, title, task))
    }

    fun notifyDone(context: Context, taskId: String, title: String, uri: Uri, mimeType: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context, doneId(taskId), view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            context,
            doneId(taskId),
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_mingo)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.status_completed_tap))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun notifyFailed(context: Context, taskId: String, title: String, message: String) {
        notify(
            context,
            doneId(taskId),
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_mingo)
                .setContentTitle(context.getString(R.string.status_failed_title, title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelProgress(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(progressId(taskId))
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

fun formatEta(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
