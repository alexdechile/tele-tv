package com.tele.app.tv.ui.player

import androidx.hilt.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.app.tv.domain.usecase.StartTorrentStreamUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TorrentStreamViewModel @Inject constructor(
    private val startTorrentStream: StartTorrentStreamUseCase
) : ViewModel() {

    private val _streamUrl = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val streamUrl: StateFlow<String?> = _streamUrl.stateIn(viewModelScope)
    val isLoading: StateFlow<Boolean> = _isLoading.stateIn(viewModelScope)
    val error: StateFlow<String?> = _error.stateIn(viewModelScope)

    fun start(magnetUri: String, fileIndex: Int) {
        viewModelScope.launch {
            _error.value = null
            _isLoading.value = true
            try {
                startTorrentStream(magnetUri, fileIndex).collect { url ->
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
