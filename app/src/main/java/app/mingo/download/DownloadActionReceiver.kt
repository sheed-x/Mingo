package app.mingo.download

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mingo.MingoApp

/** Handles the Pause and Cancel buttons on download progress notifications. */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val manager = (context.applicationContext as MingoApp).downloadManager
        when (intent.action) {
            ACTION_PAUSE -> manager.pause(id)
            ACTION_CANCEL -> manager.cancel(id)
        }
    }

    companion object {
        private const val ACTION_PAUSE = "app.mingo.action.PAUSE_DOWNLOAD"
        private const val ACTION_CANCEL = "app.mingo.action.CANCEL_DOWNLOAD"
        private const val EXTRA_TASK_ID = "task_id"

        fun pauseIntent(context: Context, taskId: String) = pending(context, taskId, ACTION_PAUSE, 2)
        fun cancelIntent(context: Context, taskId: String) = pending(context, taskId, ACTION_CANCEL, 3)

        private fun pending(context: Context, taskId: String, action: String, salt: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                taskId.hashCode() * 31 + salt,
                Intent(context, DownloadActionReceiver::class.java).setAction(action).putExtra(EXTRA_TASK_ID, taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
