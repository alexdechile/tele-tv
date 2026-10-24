package com.tele.app.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamState
import com.tele.app.tv.domain.usecase.StartTorrentStreamUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Orquesta el motor de torrents y expone su estado a la UI del reproductor. */
class TorrentStreamViewModel(
    private val engine: TorrentEngine
) : ViewModel() {

    private val startTorrentStream = StartTorrentStreamUseCase(engine)

    private val _state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Resolving)
    val state: StateFlow<TorrentStreamState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(magnetUri: String, fileIndex: Int?) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            startTorrentStream(magnetUri, fileIndex).collect { streamState ->
                _state.value = streamState
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        engine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
