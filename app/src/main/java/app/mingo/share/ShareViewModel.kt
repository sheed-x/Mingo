package app.mingo.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mingo.MingoApp
import app.mingo.engine.ErrorKind
import app.mingo.engine.MediaInfo
import app.mingo.engine.MingoError
import app.mingo.engine.toMingoError
import app.mingo.ui.download.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    val url: String,
    val phase: Phase = Phase.Analyzing,
    val info: MediaInfo? = null,
    val error: MingoError? = null,
    val selection: Selection,
) {
    enum class Phase { Analyzing, Ready, Failed, Queued }
}

class ShareViewModel(
    private val app: MingoApp,
    url: String,
    /** Skip the options and download with the defaults as soon as the link is analyzed. */
    private val instant: Boolean,
    private val extraArgs: List<String> = emptyList(),
) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState(url = url, selection = Selection.from(app.settings.settings.value)))
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        analyze()
    }

    fun analyze() {
        _state.update { it.copy(phase = ShareUiState.Phase.Analyzing, error = null) }
        viewModelScope.launch {
            if (!app.engine.awaitReady()) {
                _state.update { it.copy(phase = ShareUiState.Phase.Failed, error = MingoError(ErrorKind.Engine)) }
                return@launch
            }
            runCatching { app.engine.fetchInfo(_state.value.url) }
                .onSuccess { info ->
                    _state.update {
                        it.copy(
                            phase = ShareUiState.Phase.Ready,
                            info = info,
                            selection = Selection.from(app.settings.settings.value).fitTo(info),
                        )
                    }
                    if (instant) download()
                }
                .onFailure { e ->
                    _state.update { it.copy(phase = ShareUiState.Phase.Failed, error = app.toMingoError(e)) }
                }
        }
    }

    fun updateSelection(transform: (Selection) -> Selection) {
        _state.update { it.copy(selection = transform(it.selection)) }
    }

    fun download() {
        val s = _state.value
        val info = s.info ?: return
        app.downloadManager.enqueue(s.selection.toOptions(info).copy(extraArgs = extraArgs))
        _state.update { it.copy(phase = ShareUiState.Phase.Queued) }
    }
}
