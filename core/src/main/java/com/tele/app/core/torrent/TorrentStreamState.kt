package com.tele.app.core.torrent

/** Datos del stream listo para reproducir. */
data class TorrentStreamInfo(
    val url: String,
    val fileName: String,
    val fileSize: Long,
    val torrentName: String,
    /**
     * Subtítulos sueltos que venían en el torrent, ya servidos por el servidor
     * local. Vacío si el release no trajo ninguno (en ese caso solo quedan los
     * que vengan embebidos en el contenedor).
     */
    val subtitles: List<TorrentSubtitle> = emptyList()
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
