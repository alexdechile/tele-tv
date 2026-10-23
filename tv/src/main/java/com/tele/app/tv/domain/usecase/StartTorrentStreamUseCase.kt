package com.tele.app.tv.domain.usecase

import com.tele.app.core.torrent.TorrentEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Caso de uso: iniciar un stream de torrent y obtener la URL local para ExoPlayer.
 */
class StartTorrentStreamUseCase(private val torrentEngine: TorrentEngine) {

    operator fun invoke(magnetUri: String, fileIndex: Int): Flow<String> =
        torrentEngine.startStream(magnetUri, fileIndex).flowOn(Dispatchers.Default)
}
