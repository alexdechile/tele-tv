package com.tele.app.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.tv.domain.usecase.StartTorrentStreamUseCase

class TorrentStreamViewModel : ViewModel() {

    private val engine = TorrentEngine(viewModelScope)
    private val useCase = StartTorrentStreamUseCase(engine)

    private val _streamUrl = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(magnetUri: String, fileIndex: Int) {
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            try {
                useCase(magnetUri, fileIndex).collect { url ->
                    _streamUrl.value = url
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun stop() {
        _streamUrl.value = null
        _error.value = null
    }
}
