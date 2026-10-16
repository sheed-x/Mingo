package app.mingo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mingo.MingoApp
import app.mingo.data.AppSettings
import app.mingo.engine.ErrorKind
import app.mingo.engine.MediaInfo
import app.mingo.engine.MingoError
import app.mingo.engine.toMingoError
import app.mingo.engine.MingoEngine
import app.mingo.ui.download.Selection
import app.mingo.ui.findUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val url: String = "",
    val isFetching: Boolean = false,
    val info: MediaInfo? = null,
    val error: MingoError? = null,
    val selection: Selection = Selection.from(AppSettings()),
    /** A link found on the clipboard that the user has not acted on yet. */
    val clipboardLink: String? = null,
)

sealed interface HomeEvent {
    data class Queued(val title: String) : HomeEvent
}

class HomeViewModel(private val app: MingoApp) : ViewModel() {

    private val engine = app.engine
    val engineState: StateFlow<MingoEngine.State> = engine.state

    private val _uiState = MutableStateFlow(HomeUiState(selection = Selection.from(app.settings.settings.value)))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var fetchJob: Job? = null
    private var lastClipboardText: String? = null

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(url = url, error = null) }
    }

    fun clear() {
        fetchJob?.cancel()
        _uiState.update { it.copy(url = "", info = null, error = null, isFetching = false) }
    }

    /** Called whenever the window regains focus with whatever text the clipboard holds. */
    fun onClipboardText(text: String?) {
        val link = text?.let(::findUrl) ?: return
        if (link == lastClipboardText) return
        lastClipboardText = link
        val state = _uiState.value
        if (link == state.url) return
        val settings = app.settings.settings.value
        if (settings.autoPasteLinks && state.url.isBlank() && state.info == null) {
            onUrlChange(link)
            if (settings.autoAnalyze) fetch()
        } else {
            _uiState.update { it.copy(clipboardLink = link) }
        }
    }

    fun useClipboardLink() {
        val link = _uiState.value.clipboardLink ?: return
        _uiState.update { it.copy(clipboardLink = null) }
        onUrlChange(link)
        fetch()
    }

    fun dismissClipboardLink() {
        _uiState.update { it.copy(clipboardLink = null) }
    }

    fun fetch() {
        val url = _uiState.value.url.trim()
        if (url.isEmpty()) return
        fetchJob?.cancel()
        _uiState.update { it.copy(isFetching = true, info = null, error = null, clipboardLink = null) }
        fetchJob = viewModelScope.launch {
            if (!engine.awaitReady()) {
                _uiState.update { it.copy(isFetching = false, error = MingoError(ErrorKind.Engine)) }
                return@launch
            }
            val result = runCatching { engine.fetchInfo(url) }
            val info = result.getOrNull()
            _uiState.update { state ->
                state.copy(
                    isFetching = false,
                    info = info,
                    error = result.exceptionOrNull()?.let { app.toMingoError(it) },
                    selection = info?.let { Selection.from(app.settings.settings.value).fitTo(it) } ?: state.selection,
                )
            }
        }
    }

    fun updateSelection(transform: (Selection) -> Selection) {
        _uiState.update { it.copy(selection = transform(it.selection)) }
    }

    fun download() {
        val state = _uiState.value
        val info = state.info ?: return
        app.downloadManager.enqueue(state.selection.toOptions(info))
        _events.trySend(HomeEvent.Queued(info.title))
        _uiState.update { it.copy(url = "", info = null) }
    }

    fun retryEngine() = engine.initialize()

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(this[APPLICATION_KEY] as MingoApp) }
        }
    }
}
