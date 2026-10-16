package app.mingo.engine

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the bundled download engine: yt-dlp (running on an embedded Python), FFmpeg and aria2c.
 *
 * The first launch unpacks the Python runtime, which takes a few seconds, so initialization
 * runs in the background and the UI observes [state].
 *
 * Sites change constantly and the yt-dlp bundled in the APK goes stale quickly, so the engine
 * also pulls the latest stable yt-dlp at most once per [UPDATE_INTERVAL_MS].
 */
class MingoEngine(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("engine", Context.MODE_PRIVATE)

    sealed interface State {
        data object Initializing : State
        data object Updating : State
        data class Ready(val ytDlpVersion: String?) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Initializing)
    val state: StateFlow<State> = _state.asStateFlow()

    fun initialize() {
        if (_state.value is State.Ready) return
        _state.value = State.Initializing
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                Aria2c.getInstance().init(appContext)
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            if (isUpdateDue()) update()
            _state.value = State.Ready(currentVersion())
        }
    }

    /** Checks for a newer yt-dlp right away, regardless of when the last check happened. */
    fun updateNow() {
        if (_state.value !is State.Ready) return
        scope.launch(Dispatchers.IO) {
            update()
            _state.value = State.Ready(currentVersion())
        }
    }

    /** Suspends until initialization finishes; returns false if the engine failed to start. */
    suspend fun awaitReady(): Boolean =
        state.first { it is State.Ready || it is State.Failed } is State.Ready

    private fun isUpdateDue(): Boolean =
        System.currentTimeMillis() - prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L) >= UPDATE_INTERVAL_MS

    private fun update() {
        _state.value = State.Updating
        // A failed update (e.g. offline) is not fatal: the previously installed yt-dlp still works.
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE) }
            .onSuccess { prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()) } }
            .onFailure { Log.w(TAG, "yt-dlp update failed", it) }
    }

    private fun currentVersion(): String? =
        YoutubeDL.getInstance().version(appContext) ?: YoutubeDL.getInstance().versionName(appContext)

    /** Fetches metadata for [url] without downloading anything. */
    suspend fun fetchInfo(url: String): MediaInfo = withContext(Dispatchers.IO) {
        if (!appContext.isOnline()) throw EngineException(MingoError(ErrorKind.Offline))
        val info = try {
            YoutubeDL.getInstance().getInfo(url)
        } catch (e: Exception) {
            throw EngineException(appContext.toMingoError(e))
        }
        MediaInfo.from(url, info)
    }

    private companion object {
        const val TAG = "MingoEngine"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
