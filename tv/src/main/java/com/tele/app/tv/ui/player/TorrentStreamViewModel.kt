package com.tele.app.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamInfo
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

    /**
     * URL del stream, guardada aparte del estado de progreso.
     *
     * El motor emite `Ready` y en el mismo instante empieza a emitir `Progress`
     * (una vez por segundo). Como `StateFlow` conflaciona, la UI se quedaba con
     * `Progress` y **nunca veía `Ready`**: ExoPlayer no recibía la URL y el
     * reproductor quedaba en negro aunque el servidor local estuviera sirviendo
     * el video perfectamente. Acá la URL se fija una vez y no la pisa nadie.
     */
    private val _ready = MutableStateFlow<TorrentStreamInfo?>(null)
    val ready: StateFlow<TorrentStreamInfo?> = _ready.asStateFlow()

    private var job: Job? = null

    fun start(magnetUri: String, fileIndex: Int?) {
        if (job?.isActive == true) return
        _ready.value = null
        job = viewModelScope.launch {
            startTorrentStream(magnetUri, fileIndex).collect { streamState ->
                if (streamState is TorrentStreamState.Ready) {
                    _ready.value = streamState.info
                }
                _state.value = streamState
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _ready.value = null
        engine.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
