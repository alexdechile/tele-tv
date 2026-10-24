package com.tele.app.core.torrent

/** Datos del stream listo para reproducir. */
data class TorrentStreamInfo(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val torrentName: String
)

/** Estado del motor de torrents mientras prepara y sirve el video. */
sealed interface TorrentStreamState {

    /** Resolviendo metadatos del magnet (DHT/trackers). */
    data object Resolving : TorrentStreamState

    /** Ya hay metadatos y servidor local: ExoPlayer puede reproducir [info.url]. */
    data class Ready(val info: TorrentStreamInfo) : TorrentStreamState

    /** Progreso de descarga mientras se reproduce. */
    data class Progress(
        val percent: Float,
        val downloadRate: Long,
        val peers: Int,
        val state: String
    ) : TorrentStreamState

    data class Error(val message: String) : TorrentStreamState
}
