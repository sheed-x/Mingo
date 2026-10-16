package app.mingo.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mingo.MingoApp
import app.mingo.data.DownloadType
import app.mingo.data.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibrarySort { Newest, Oldest, Name, Size }

data class LibraryUiState(
    val all: List<LibraryItem> = emptyList(),
    val visible: List<LibraryItem> = emptyList(),
    val query: String = "",
    /** null shows everything. */
    val filter: DownloadType? = null,
    val sort: LibrarySort = LibrarySort.Newest,
) {
    val totalBytes: Long get() = all.sumOf { it.sizeBytes }
}

class LibraryViewModel(private val app: MingoApp) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<DownloadType?>(null)
    private val sort = MutableStateFlow(LibrarySort.Newest)

    val state: StateFlow<LibraryUiState> = combine(app.library.items, query, filter, sort) { items, q, f, s ->
        val matching = items.filter { item ->
            (f == null || item.options.type == f) &&
                (q.isBlank() || listOfNotNull(item.title, item.options.uploader, item.options.extractor)
                    .any { it.contains(q.trim(), ignoreCase = true) })
        }
        LibraryUiState(
            all = items,
            visible = when (s) {
                LibrarySort.Newest -> matching.sortedByDescending { it.createdAt }
                LibrarySort.Oldest -> matching.sortedBy { it.createdAt }
                LibrarySort.Name -> matching.sortedBy { it.title.lowercase() }
                LibrarySort.Size -> matching.sortedByDescending { it.sizeBytes }
            },
            query = q,
            filter = f,
            sort = s,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // Files deleted in another app (e.g. Files by Google) disappear from the library.
        app.library.pruneMissing()
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onFilterChange(value: DownloadType?) { filter.value = value }
    fun onSortChange(value: LibrarySort) { sort.value = value }

    fun delete(item: LibraryItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(app.library.delete(item)) }
    }

    fun redownload(item: LibraryItem) {
        app.downloadManager.enqueue(item.options)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(this[APPLICATION_KEY] as MingoApp) }
        }
    }
}
