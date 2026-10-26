package com.tele.app.core.torrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Motor de torrents real (libtorrent4j).
 *
 * Flujo de trabajo:
 *  1. Una única `SessionManager` (la sesión de libtorrent) para toda la app.
 *  2. Se agrega el magnet y se esperan los metadatos (DHT/trackers).
 *  3. Se elige el archivo de video y se dejan **todas** las demás piezas en
 *     IGNORE, así el ancho de banda se concentra en la película.
 *  4. Descarga secuencial sobre el rango de piezas de ese archivo.
 *  5. Se levanta [HttpStreamServer] y se entrega su URL a ExoPlayer.
 *
 * ExoPlayer nunca ve el torrent: recibe `http://127.0.0.1:PORT/stream`.
 */
class TorrentEngine(private val saveDir: File) {

    private val lock = Any()

    private var sessionManager: SessionManager? = null
    private var activeServer: HttpStreamServer? = null
    private var activeHandle: TorrentHandle? = null

    init {
        this.saveDir.mkdirs()
    }

    /** Prepara el stream y emite su estado; termina cuando se cancela la corrutina. */
    fun stream(magnetUri: String, preferredFileIndex: Int?): Flow<TorrentStreamState> = flow {
        try {
            runStream(magnetUri, preferredFileIndex)
        } catch (cancellation: CancellationException) {
            stop()
            throw cancellation
        } catch (error: Exception) {
            stop()
            emit(TorrentStreamState.Error(error.message ?: "Error inesperado del motor de torrents"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<TorrentStreamState>.runStream(
        magnetUri: String,
        preferredFileIndex: Int?
    ) {
        emit(TorrentStreamState.Resolving)

        val manager = ensureSession()
        val infoHash = parseInfoHash(magnetUri)
        if (infoHash == null) {
            emit(TorrentStreamState.Error("Magnet inválido: no se pudo leer el info hash"))
            return
        }

        stop()

        manager.download(magnetUri, saveDir, torrent_flags_t())

        val handle = awaitTorrent(manager, infoHash)
        if (handle == null) {
            emit(
                TorrentStreamState.Error(
                    "Sin metadatos del torrent. Revisa que tenga seeds o trackers activos."
                )
            )
            return
        }

        val torrentInfo = handle.torrentFile()
        if (torrentInfo == null) {
            emit(TorrentStreamState.Error("El torrent no entregó metadatos utilizables"))
            return
        }

        val files = torrentInfo.files()
        val fileIndex = TorrentVideoFiles.pick(files, preferredFileIndex)
        if (fileIndex == null) {
            emit(TorrentStreamState.Error("El torrent no contiene archivos de video"))
            return
        }

        prepareDownload(handle, torrentInfo, fileIndex)

        val fileName = files.fileName(fileIndex)
        val fileSize = files.fileSize(fileIndex)

        val firstPiece = files.pieceIndexAtFile(fileIndex)
        val lastPiece = files.lastPieceIndexAtFile(fileIndex)

        // Priorizar primera y última pieza para lectura rápida de cabeceras (moov/EBML)
        runCatching { handle.piecePriority(firstPiece, Priority.TOP_PRIORITY) }
        runCatching { handle.piecePriority(lastPiece, Priority.TOP_PRIORITY) }

        // Esperar a tener la primera pieza para que ExoPlayer lea la cabecera sin timeout
        val pieceDeadline = System.currentTimeMillis() + FIRST_PIECE_TIMEOUT_MS
        while (currentCoroutineContext().isActive && !handle.havePiece(firstPiece) && System.currentTimeMillis() < pieceDeadline) {
            emitProgress(handle)?.let { emit(it) }
            delay(PROGRESS_INTERVAL_MS)
        }

        val server = HttpStreamServer(
            pieceLength = torrentInfo.pieceLength().toLong(),
            numPieces = torrentInfo.numPieces(),
            file = File(saveDir, files.filePath(fileIndex)),
            fileOffsetInTorrent = files.fileOffset(fileIndex),
            fileSize = fileSize,
            contentType = TorrentVideoFiles.contentTypeOf(fileName),
            pieces = TorrentPieces(handle)
        )
        server.start()

        synchronized(lock) {
            activeServer = server
            activeHandle = handle
        }

        emit(
            TorrentStreamState.Ready(
                TorrentStreamInfo(
                    url = server.url,
                    fileName = fileName,
                    fileSize = fileSize,
                    torrentName = torrentInfo.name()
                )
            )
        )

        while (currentCoroutineContext().isActive) {
            emitProgress(handle)?.let { emit(it) }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    /** Corta el torrent activo y cierra el servidor local. */
    fun stop() {
        synchronized(lock) {
            activeServer?.stop()
            activeServer = null

            val handle = activeHandle
            activeHandle = null
            val manager = sessionManager
            if (handle != null && manager != null) {
                runCatching { manager.remove(handle) }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Interno
    // ---------------------------------------------------------------------

    private fun ensureSession(): SessionManager = synchronized(lock) {
        sessionManager ?: SessionManager().also { manager ->
            manager.start()
            sessionManager = manager
        }
    }

    private fun parseInfoHash(magnetUri: String): Sha1Hash? = try {
        AddTorrentParams.parseMagnetUri(magnetUri).infoHashes.best
    } catch (error: Exception) {
        null
    }

    /** Espera a que libtorrent tenga el `torrent_info` (metadatos listos). */
    private suspend fun awaitTorrent(manager: SessionManager, infoHash: Sha1Hash): TorrentHandle? {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val handle = try {
                manager.find(infoHash)
            } catch (error: Exception) {
                null
            }

            if (handle != null) {
                val ready = try {
                    handle.isValid() && handle.torrentFile() != null
                } catch (error: Exception) {
                    false
                }
                if (ready) return handle
            }
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    /** Prioriza solo el archivo elegido y activa la descarga secuencial. */
    private fun prepareDownload(handle: TorrentHandle, torrentInfo: TorrentInfo, fileIndex: Int) {
        for (index in 0 until torrentInfo.numFiles()) {
            val priority = if (index == fileIndex) Priority.TOP_PRIORITY else Priority.IGNORE
            runCatching { handle.filePriority(index, priority) }
        }

        val files = torrentInfo.files()
        val firstPiece = files.pieceIndexAtFile(fileIndex)
        val lastPiece = files.lastPieceIndexAtFile(fileIndex)
        runCatching { handle.setSequentialRange(firstPiece, lastPiece) }
    }

    private fun emitProgress(handle: TorrentHandle): TorrentStreamState.Progress? = try {
        val status = handle.status()
        TorrentStreamState.Progress(
            percent = (status.progress() * 100f).coerceIn(0f, 100f),
            downloadRate = status.downloadRate().toLong(),
            peers = status.numPeers(),
            state = status.state().name
        )
    } catch (error: Exception) {
        null
    }

    private companion object {
        const val METADATA_TIMEOUT_MS = 60_000L
        const val FIRST_PIECE_TIMEOUT_MS = 60_000L
        const val POLL_INTERVAL_MS = 300L
        const val PROGRESS_INTERVAL_MS = 1_000L
    }
}
