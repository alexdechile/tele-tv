package com.tele.app.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableStateFlow
import androidx.compose.runtime.StateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.value
import com.tele.app.tv.domain.usecase.StartTorrentStreamUseCase

class TorrentStreamViewModel(
    private val startTorrentStream: StartTorrentStreamUseCase
) {

    private val _streamUrl = mutableStateOf<String?>(null)
    private val _isLoading = mutableStateOf(false)
    private val _error = mutableStateOf<String?>(null)

    val streamUrl: String? by _streamUrl
    val isLoading: Boolean by _isLoading
    val error: String? by _error

    fun start(magnetUri: String, fileIndex: Int) {
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

    fun stop() {
        _streamUrl.value = null
        _error.value = null
    }
}
