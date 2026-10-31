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
import java.util.concurrent.Executors

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
class TorrentEngine(
    /** Directorio raíz de la caché. Cada reproducción usa un subdirectorio propio. */
    private val saveDir: File,
    /**
     * Ajustes de la sesión. Se aplican una sola vez, al crear la sesión; por
     * defecto vienen endurecidos (sin UPnP, sin NAT-PMP, sin LSD).
     */
    private val settings: TorrentEngineSettings = TorrentEngineSettings(),
    /**
     * Borrar el material al terminar. Es el comportamiento por defecto: la TV
     * box no es un disco de descargas, se ve el video y se va. Con `false` el
     * archivo queda en la caché (solo para depurar a mano).
     */
    private val deleteOnStop: Boolean = true
) {

    /** Se avisa acá si el borrado no pudo completarse (para no fallar en silencio). */
    var onCleanupProblem: ((String) -> Unit)? = null

    /** Trazas del motor (estado y peers mientras se esperan metadatos). */
    var onDiagnostic: ((String) -> Unit)? = null

    private val lock = Any()

    private var sessionManager: SessionManager? = null
    private var activeServer: HttpStreamServer? = null
    private var activeHandle: TorrentHandle? = null

    /** Directorio de la reproducción en curso; se borra al parar. */
    private var activeDir: File? = null

    /**
     * Hilo aparte para borrar: `stop()` lo llaman la UI y la cancelación de la
     * corrutina, y borrar un archivo que libtorrent acaba de soltar puede
     * tardar un poco. No se bloquea al llamador por eso.
     */
    private val cleanupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "torrent-cleanup").apply { isDaemon = true }
    }

    init {
        this.saveDir.mkdirs()
    }

    /** Prepara el stream y emite su estado; termina cuando se cancela la corrutina. */
    fun stream(magnetUri: String, preferredFileIndex: Int?): Flow<TorrentStreamState> = flow {
        try {
            runStream(magnetUri, preferredFileIndex)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emit(TorrentStreamState.Error(error.message ?: "Error inesperado del motor de torrents"))
        } finally {
            // También en los caminos de error: si el torrent quedó a medio bajar
            // (sin seeds, metadatos vencidos), no se deja el material en disco.
            stop()
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

        // Cada reproducción baja a su propio subdirectorio. Así el borrado es
        // quirúrgico (no se toca nada de otra sesión), dos reproducciones
        // seguidas no se pisan, y si el proceso muere queda un directorio
        // identificable que la app barre en el siguiente arranque.
        val sessionDir = File(saveDir, "session-${safeDirName(infoHash)}-${System.currentTimeMillis()}")
        synchronized(lock) { activeDir = sessionDir }

        manager.download(magnetUri, sessionDir, torrent_flags_t())

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

        // El inicio del archivo manda: sin esas piezas el reproductor no puede
        // leer ni la cabecera del MP4/MKV y la pantalla queda negra. La cola
        // (moov/Cues al final) se pide cuando ExoPlayer la pida, no antes: si
        // compite con el inicio solo lo retrasa.
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)

        // Esperar a tener la primera pieza para que ExoPlayer lea la cabecera sin
        // timeout. Se reafirma la prioridad en cada vuelta: libtorrent la
        // reacomoda al entrar peers nuevos y sin esto la ventana se diluye.
        val pieceDeadline = System.currentTimeMillis() + FIRST_PIECE_TIMEOUT_MS
        while (currentCoroutineContext().isActive && !handle.havePiece(firstPiece)) {
            StreamPriorities.boostHead(handle, firstPiece, lastPiece)
            emitProgress(handle)?.let { emit(it) }
            if (System.currentTimeMillis() >= pieceDeadline) break
            delay(PROGRESS_INTERVAL_MS)
        }

        // La ruta la escribe el torrent, no la app: se resuelve a la fuerza
        // dentro del directorio de esta sesión. Si el torrent trae `../..` o
        // una ruta absoluta, se corta acá en vez de leer fuera de la caché.
        val videoFile = TorrentPaths.resolveWithin(sessionDir, files.filePath(fileIndex))
        if (videoFile == null) {
            emit(
                TorrentStreamState.Error(
                    "El torrent trae una ruta de archivo no permitida: ${files.filePath(fileIndex)}"
                )
            )
            return
        }

        val server = HttpStreamServer(
            pieceLength = torrentInfo.pieceLength().toLong(),
            numPieces = torrentInfo.numPieces(),
            file = videoFile,
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

    /**
     * Corta el torrent activo, cierra el servidor local y **borra lo bajado**.
     *
     * Ojo con el orden que importa: el `TorrentHandle` se saca de la sesión
     * antes de tocar el disco, porque mientras libtorrent tenga el archivo
     * abierto el borrado puede fallar en Android.
     */
    fun stop() {
        val dirToDelete: File?
        synchronized(lock) {
            activeServer?.stop()
            activeServer = null

            val handle = activeHandle
            activeHandle = null
            val manager = sessionManager
            if (handle != null && manager != null) {
                runCatching { manager.remove(handle) }
            }

            dirToDelete = activeDir
            activeDir = null
        }

        if (deleteOnStop) {
            dirToDelete?.let { scheduleDelete(it) }
        }
    }

    /**
     * Cierra la sesión de libtorrent además del torrent activo.
     *
     * Sin esto cada visita al reproductor dejaba una sesión nativa viva con su
     * socket de escucha y sus trackers: la app seguía "presente" en la red
     * después de salir del video. Se llama al desmontar la pantalla.
     */
    fun shutdown() {
        stop()
        val manager = synchronized(lock) {
            val current = sessionManager
            sessionManager = null
            current
        }
        if (manager != null) {
            runCatching { manager.stop() }
        }
        cleanupExecutor.shutdown()
    }

    // ---------------------------------------------------------------------
    // Interno
    // ---------------------------------------------------------------------

    /**
     * Borra en segundo plano, con reintentos. Si aun así queda algo, se avisa
     * por [onCleanupProblem] y la app lo barre en el siguiente arranque
     * (`TeleTvApp`), así que nunca se acumula para siempre.
     */
    private fun scheduleDelete(dir: File) {
        val queued = runCatching {
            cleanupExecutor.execute {
                var attempts = 0
                while (dir.exists() && attempts < DELETE_ATTEMPTS) {
                    val deleted = runCatching { dir.deleteRecursively() }.getOrDefault(false)
                    if (deleted || !dir.exists()) return@execute
                    attempts++
                    if (attempts < DELETE_ATTEMPTS) {
                        runCatching { Thread.sleep(DELETE_RETRY_DELAY_MS) }
                    }
                }
                if (dir.exists()) {
                    onCleanupProblem?.invoke(
                        "No se pudo borrar ${dir.name}: quedan " +
                            "${TorrentPaths.sizeOf(dir)} bytes en la caché"
                    )
                }
            }
        }.isSuccess

        if (!queued) {
            // El executor ya se cerró (shutdown): se intenta igual, sincrónico.
            runCatching { dir.deleteRecursively() }
        }
    }

    /** Nombre de directorio sin nada raro: solo letras y dígitos del hash. */
    private fun safeDirName(infoHash: Sha1Hash): String =
        infoHash.toString().filter { it.isLetterOrDigit() }.lowercase().ifEmpty { "sin-hash" }

    private fun ensureSession(): SessionManager = synchronized(lock) {
        sessionManager ?: SessionManager().also { manager ->
            // start(params) y no start(): los parámetros llevan el
            // settings_pack de TorrentEngineSettings, que es justamente lo que
            // apaga UPnP, NAT-PMP y LSD y pone el tope de subida.
            manager.start(settings.toSessionParams())
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
        var lastDiagnostic = 0L
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

                // Diagnóstico: si los metadatos no llegan, esto dice si el
                // problema son los peers (0) o el anuncio (estado). Sin esto,
                // "Obteniendo metadatos…" es indistinguible de "tracker caído".
                val now = System.currentTimeMillis()
                if (now - lastDiagnostic >= DIAGNOSTIC_INTERVAL_MS) {
                    lastDiagnostic = now
                    runCatching { handle.status() }.getOrNull()?.let { status ->
                        onDiagnostic?.invoke(
                            "metadatos: estado=${status.state().name} peers=${status.numPeers()}"
                        )
                    }
                }
            }
            delay(POLL_INTERVAL_MS)
        }
        return null
    }

    /** Prioriza solo el archivo elegido y activa la descarga secuencial. */
    private fun prepareDownload(handle: TorrentHandle, torrentInfo: TorrentInfo, fileIndex: Int) {
        for (index in 0 until torrentInfo.numFiles()) {
            // DEFAULT y no TOP_PRIORITY: si *todas* las piezas quedan en TOP, la
            // prioridad no ordena nada y libtorrent reparte por "rarest first".
            // Eso es justo lo que hacía llegar el inicio del archivo al final de
            // la descarga (el video arrancaba recién cerca del 100 %).
            val priority = if (index == fileIndex) Priority.DEFAULT else Priority.IGNORE
            runCatching { handle.filePriority(index, priority) }
        }

        val files = torrentInfo.files()
        val firstPiece = files.pieceIndexAtFile(fileIndex)
        val lastPiece = files.lastPieceIndexAtFile(fileIndex)
        runCatching { handle.setSequentialRange(firstPiece, lastPiece) }

        // La ventana del inicio se fuerza aparte, con deadline: es lo que hace
        // que llegue en segundos en vez de "cuando toque".
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)
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
        const val DIAGNOSTIC_INTERVAL_MS = 3_000L

        /** Reintentos de borrado: libtorrent tarda un instante en soltar el archivo. */
        const val DELETE_ATTEMPTS = 8
        const val DELETE_RETRY_DELAY_MS = 250L
    }
}
