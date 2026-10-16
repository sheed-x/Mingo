package app.mingo.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import app.mingo.download.DownloadOptions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A finished download kept in the library. */
@Serializable
data class LibraryItem(
    val id: String,
    val options: DownloadOptions,
    val uri: String,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: Long,
    /** Absolute path on Android 9 and older, where files are not managed by MediaStore. */
    val path: String? = null,
) {
    val title: String get() = options.title
    val isAudio: Boolean get() = options.type == DownloadType.Audio
}

/**
 * Persists the library as a small JSON file in app storage. Libraries are at most a few thousand
 * entries, so reading the whole file once and rewriting it on change is simple and fast enough.
 */
class LibraryRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    /** Newest first. */
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = runCatching { json.decodeFromString<List<LibraryItem>>(file.readText()) }
                .onFailure { if (file.exists()) Log.w(TAG, "Could not read library", it) }
                .getOrDefault(emptyList())
            _items.update { current -> (current + loaded).distinctBy { it.id }.sortedByDescending { it.createdAt } }
        }
    }

    fun add(item: LibraryItem) {
        _items.update { list -> listOf(item) + list.filterNot { it.id == item.id } }
        persist()
    }

    /** Deletes the file from the device and drops it from the library. Returns false if the file could not be deleted. */
    suspend fun delete(item: LibraryItem): Boolean = withContext(Dispatchers.IO) {
        val deleted = runCatching {
            if (item.path != null) File(item.path).delete() || !File(item.path).exists()
            else appContext.contentResolver.delete(item.uri.toUri(), null, null) > 0 || !exists(item)
        }.getOrDefault(false)
        forget(item.id)
        deleted
    }

    /** Removes entries whose file was deleted outside the app (e.g. in the Files app). */
    fun pruneMissing() {
        scope.launch(Dispatchers.IO) {
            val missing = _items.value.filterNot(::exists).map { it.id }.toSet()
            if (missing.isNotEmpty()) {
                _items.update { list -> list.filterNot { it.id in missing } }
                persist()
            }
        }
    }

    private fun forget(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    private fun exists(item: LibraryItem): Boolean {
        if (item.path != null) return File(item.path).exists()
        return runCatching {
            appContext.contentResolver.openFileDescriptor(Uri.parse(item.uri), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                // Read the latest state under the lock so a slow earlier write can never win.
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(_items.value))
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val TAG = "LibraryRepository"
    }
}
