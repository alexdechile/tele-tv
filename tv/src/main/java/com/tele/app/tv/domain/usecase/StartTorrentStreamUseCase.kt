package com.tele.app.tv.domain.usecase

import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamState
import kotlinx.coroutines.flow.Flow

/**
 * Caso de uso: preparar un torrent y obtener la URL local para ExoPlayer.
 *
 * La URL llega dentro de [TorrentStreamState.Ready]; el resto del flujo son
 * actualizaciones de progreso mientras se reproduce.
 */
class StartTorrentStreamUseCase(private val torrentEngine: TorrentEngine) {

    operator fun invoke(magnetUri: String, fileIndex: Int?): Flow<TorrentStreamState> =
        torrentEngine.stream(magnetUri, fileIndex)
}
