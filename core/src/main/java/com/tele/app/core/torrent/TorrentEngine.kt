package com.tele.app.core.torrent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.InfoHash
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.Sha256Hash
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.FileStorage
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import java.io.File
import java.util.concurrent.Executors

/**
 * Motor de torrents real (jlibtorrent 2.0.15.3 → libtorrent 2.0).
 *
 * libtorrent 2.0 es la primera generación con BitTorrent **v2** nativo: acepta
 * magnets `btmh:` (info-hash SHA-256), torrents híbridos v1+v2 y el árbol de
 * archivos de v2. [awaitTorrent] busca por el hash v2 y solo cae al v1 si el
 * torrent no lo tiene.
 *
 * Flujo de trabajo:
 *  1. Una única `SessionManager` (la sesión de libtorrent) para toda la app.
 *  2. Se agrega el magnet y se esperan los metadatos (DHT/trackers).
 *  3. Se elige el archivo de video y se dejan **todas** las demás piezas en
 *     IGNORE, así el ancho de banda se concentra en la película.
 *  4. El inicio del archivo se pide con prioridad máxima y deadline de
 *     streaming (ver `StreamPriorities`); el picker de libtorrent decide el
 *     resto.
 *  5. Se levanta [HttpStreamServer] y se entrega su URL a ExoPlayer.
 *  6. Cuando el archivo elegido termina de bajar, el torrent se **pausa**: se
 *     corta la subida (peers y anuncios) y la reproducción sigue desde el disco.
 *     Al salir del reproductor, [stop] cierra todo y borra la caché.
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

    /**
     * Si es true, la descarga actual se conserva en disco al salir (no se borra en stop).
     * Puede conmutarse desde la interfaz con el botón de conservar.
     */
    @Volatile
    var keepOnStop: Boolean = false

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

        val range = pieceRangeForFile(files, fileIndex)
        val firstPiece = range.first
        val lastPiece = range.last

        // El inicio del archivo manda: sin esas piezas el reproductor no puede
        // leer ni la cabecera del MP4/MKV y la pantalla queda negra. La cola
        // (moov/Cues al final) se pide cuando ExoPlayer la pida, no antes: si
        // compite con el inicio solo lo retrasa.
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)

        // Esperar a tener la primera pieza para que ExoPlayer lea la cabecera sin
        // timeout. Se reafirma la prioridad en cada vuelta: libtorrent la
        // reacomoda al entrar peers nuevos y sin esto la ventana se diluye.
        val pieceDeadline = System.currentTimeMillis() + FIRST_PIECE_TIMEOUT_MS
        val waitStart = System.currentTimeMillis()
        val head = StreamPriorities.headPieces(firstPiece, lastPiece)
        var headReported = 0L
        while (currentCoroutineContext().isActive && !handle.havePiece(firstPiece)) {
            // Solo prioridad: el deadline se puso una vez en prepareDownload.
            // Re-aplicarlo cada vuelta reordena las peticiones y cancela los
            // bloques en vuelo (medido: cola mezclada y primera pieza a 26 s).
            StreamPriorities.reaffirmPriorities(handle, firstPiece, lastPiece)
            emitProgress(handle)?.let { emit(it) }
            // Cuántas piezas del inicio ya están: distingue "el head avanza en
            // orden, lento" de "libtorrent ignora el head y baja otra cosa".
            val nowHead = System.currentTimeMillis()
            if (nowHead - headReported >= DIAGNOSTIC_INTERVAL_MS) {
                headReported = nowHead
                val got = head.count { runCatching { handle.havePiece(it) }.getOrDefault(false) }
                // Prioridad realmente aplicada y qué piezas se están pidiendo:
                // sin esto no se distingue "libtorrent ignora el deadline" de
                // "la prioridad nunca se aplicó".
                val prios = head.map { runCatching { handle.piecePriority(it).swig() }.getOrDefault(-1) }
                val queue = runCatching {
                    handle.downloadQueue.take(6).joinToString(",") { it.pieceIndex().toString() }
                }.getOrDefault("?")
                onDiagnostic?.invoke(
                    "head: $got/${head.last - head.first + 1} piezas del inicio " +
                        "prios=$prios cola=[$queue]"
                )
            }
            if (System.currentTimeMillis() >= pieceDeadline) break
            delay(PROGRESS_INTERVAL_MS)
        }

        // El inicio ya está (o se agotó la espera): se abre el head completo.
        // El resto del archivo sigue en IGNORE hasta que el head esté entero;
        // liberarlo ahora repetiría la dispersión justo cuando ExoPlayer empieza
        // a pedir su buffer (medido: 37 s entre la primera pieza y el decoder).
        expandHead(handle, firstPiece, lastPiece)
        // Cuánto costó la primera pieza y si de verdad llegó. Es la métrica que
        // decide el tiempo al primer frame: si esto tarda, el video arranca tarde
        // aunque la bajada vaya rápido.
        val haveFirst = runCatching { handle.havePiece(firstPiece) }.getOrDefault(false)
        onDiagnostic?.invoke(
            "primera pieza: piece=$firstPiece have=$haveFirst " +
                "espera=${System.currentTimeMillis() - waitStart}ms"
        )

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

        // Los subtítulos sueltos (.srt/.vtt/.ass) se sirven por el mismo
        // servidor: ExoPlayer los pide como una URL más y el servidor espera la
        // pieza igual que con el video.
        val subtitles = prepareSubtitles(handle, torrentInfo, fileIndex, sessionDir)

        val server = HttpStreamServer(
            pieceLength = torrentInfo.pieceLength().toLong(),
            numPieces = torrentInfo.numPieces(),
            file = videoFile,
            fileOffsetInTorrent = files.fileOffset(fileIndex),
            fileSize = fileSize,
            contentType = TorrentVideoFiles.contentTypeOf(fileName),
            pieces = TorrentPieces(handle),
            sidecars = subtitles.map { it.sidecar }
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
                    torrentName = torrentInfo.name(),
                    subtitles = subtitles.mapIndexed { index, prepared ->
                        TorrentSubtitle(
                            url = server.sidecarUrl(index),
                            fileName = prepared.fileName,
                            label = prepared.label,
                            language = prepared.language,
                            contentType = prepared.contentType
                        )
                    }
                )
            )
        )

        // El video puede terminar de bajar mientras se sigue viendo. Cuando eso
        // pasa no hay nada más que pedir: se corta la subida pausando el
        // torrent, y la reproducción sigue leyendo del disco sin enterarse.
        var uploadPaused = false
        var restReleased = false

        while (currentCoroutineContext().isActive) {
            emitProgress(handle)?.let { emit(it) }
            // Fase 3: con el head entero en disco se habilita el resto del
            // archivo. Antes no, para no competir con el arranque del player.
            if (!restReleased && headIsComplete(handle, firstPiece, lastPiece)) {
                restReleased = true
                releaseRestOfFile(handle, fileIndex, firstPiece, lastPiece)
            }
            if (!uploadPaused) {
                uploadPaused = pauseIfComplete(handle, fileIndex, fileSize)
            }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    /**
     * Pausa el torrent cuando el archivo elegido ya está entero en disco.
     *
     * Se pausa en vez de `remove()` porque pausar conserva el `TorrentHandle`
     * (sirve para el progreso y para el cierre ordenado de [stop]) y hace lo
     * mismo que importa: libtorrent suelta los peers, deja de anunciarse en los
     * trackers y no sube ni un byte más.
     */
    private fun pauseIfComplete(handle: TorrentHandle, fileIndex: Int, fileSize: Long): Boolean {
        val paused = StreamCompletion.pauseIfComplete(
            fileIndex = fileIndex,
            fileSize = fileSize,
            fileProgress = { runCatching { handle.fileProgress() }.getOrNull() },
            pause = { handle.pause() }
        )
        if (paused) {
            onDiagnostic?.invoke(
                "descarga completa: torrent pausado, subida cortada " +
                    "(${fileSize / (1024 * 1024)} MB en disco)"
            )
        }
        return paused
    }

    /**
     * Marca si la descarga activa debe conservarse en disco al salir.
     */
    fun setKeepDownloaded(keep: Boolean) {
        keepOnStop = keep
        onDiagnostic?.invoke(
            if (keep) "descarga marcada para conservar en disco"
            else "descarga marcada para eliminar al salir"
        )
    }

    /**
     * Consulta si la descarga está marcada para conservarse en disco.
     */
    fun isKeepDownloaded(): Boolean = keepOnStop

    /**
     * Borra inmediatamente el material descargado del torrent activo y libera el espacio.
     * Retorna true si se liberaron datos o se borró el directorio exitosamente.
     */
    fun deleteActiveDownloadNow(): Boolean {
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
            keepOnStop = false
        }

        return if (dirToDelete != null) {
            val freed = TorrentPaths.purge(dirToDelete)
            val deleted = runCatching { dirToDelete.deleteRecursively() }.getOrDefault(false)
            onDiagnostic?.invoke("archivo eliminado completamente por el usuario ($freed bytes liberados)")
            deleted || freed > 0
        } else {
            false
        }
    }

    /**
     * Corta el torrent activo, cierra el servidor local y borra lo bajado
     * (a menos que se haya marcado [keepOnStop] en true).
     *
     * Ojo con el orden que importa: el `TorrentHandle` se saca de la sesión
     * antes de tocar el disco, porque mientras libtorrent tenga el archivo
     * abierto el borrado puede fallar en Android.
     */
    fun stop() {
        val dirToDelete: File?
        val preservedDir: File?
        synchronized(lock) {
            activeServer?.stop()
            activeServer = null

            val handle = activeHandle
            activeHandle = null
            val manager = sessionManager
            if (handle != null && manager != null) {
                runCatching { manager.remove(handle) }
            }

            if (keepOnStop) {
                dirToDelete = null
                preservedDir = activeDir
            } else {
                dirToDelete = activeDir
                preservedDir = null
            }
            activeDir = null
        }

        if (preservedDir != null) {
            onDiagnostic?.invoke("descarga conservada en almacenamiento: ${preservedDir.absolutePath}")
        } else if (deleteOnStop && dirToDelete != null) {
            scheduleDelete(dirToDelete)
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

    /** Última traza de tasas emitida (para no escribir logcat en cada tick). */
    private var lastRateDiagnostic = 0L

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

    /**
     * Nombre de directorio sin nada raro: solo letras y dígitos del hash.
     *
     * Usa `getBest()`, que en jlibtorrent devuelve el **v1 truncado** (su firma
     * es `Sha1Hash`), no el SHA-256. Es a propósito: ese v1 truncado existe
     * también para los torrents v2 puros y es estable entre sesiones, así que
     * sirve como nombre de carpeta sin depender de qué versión de protocolo
     * traiga el magnet.
     */
    private fun safeDirName(infoHash: InfoHash): String =
        infoHash.getBest().toString().filter { it.isLetterOrDigit() }.lowercase().ifEmpty { "sin-hash" }

    private fun ensureSession(): SessionManager = synchronized(lock) {
        sessionManager ?: SessionManager().also { manager ->
            // start(params) y no start(): los parámetros llevan el
            // settings_pack de TorrentEngineSettings, que es justamente lo que
            // apaga UPnP, NAT-PMP y LSD y pone el tope de subida.
            manager.start(settings.toSessionParams())
            sessionManager = manager
        }
    }

    private fun parseInfoHash(magnetUri: String): InfoHash? = try {
        AddTorrentParams.parseMagnetUri(magnetUri).infoHashes
    } catch (error: Exception) {
        null
    }

    /** Espera a que libtorrent tenga el `torrent_info` (metadatos listos). */
    private suspend fun awaitTorrent(manager: SessionManager, infoHash: InfoHash): TorrentHandle? {
        val deadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        var lastDiagnostic = 0L
        while (System.currentTimeMillis() < deadline) {
            val handle = try {
                // Preferir v2 (SHA-256) para seguridad; fallback a v1
                val sha256 = infoHash.getV2()
                val sha1 = infoHash.getV1()
                var h = if (infoHash.hasV2()) manager.find(sha256) else manager.find(sha1)
                if (h == null && infoHash.hasV1()) h = manager.find(sha1)
                h
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
            // NORMAL y no SEVEN: si *todas* las piezas quedan en SEVEN, la
            // prioridad no ordena nada y libtorrent reparte por "rarest first".
            // Eso es justo lo que hacía llegar el inicio del archivo al final de
            // la descarga (el video arrancaba recién cerca del 100 %).
            val priority = if (index == fileIndex) Priority.NORMAL else Priority.IGNORE
            runCatching { handle.filePriority(index, priority) }
        }

        val files = torrentInfo.files()
        val range = pieceRangeForFile(files, fileIndex)
        val firstPiece = range.first
        val lastPiece = range.last

        // **Sin SEQUENTIAL_DOWNLOAD a propósito.** Medido en el KM6 con
        // jlibtorrent 2.0.15.3: con la flag puesta, el picker pasa a orden
        // secuencial del torrent y deja de honrar los deadlines de streaming;
        // el resultado fue `head: 0/8` durante 20 s bajando a 1,2 MB/s y la
        // primera pieza llegó recién a los 52 s. El orden secuencial del resto
        // se activa después de tener el inicio (ver más abajo).

        // La ventana del inicio se fuerza aparte, con deadline: es lo que hace
        // que llegue en segundos en vez de "cuando toque".
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)

        // Y el resto del archivo queda en IGNORE hasta que el inicio llegue.
        // Medido en el KM6: con las 1268 piezas descargables a la vez y 178
        // peers, el picker pide rarest-first en todo el archivo (cola real:
        // 0,1,388,1164,82,549) y reparte el ancho de banda entre decenas de
        // piezas incompletas; la primera pieza tardó entre 26 y 52 s y el
        // primer frame hasta 148 s. Con el resto en IGNORE solo compite el head.
        concentrateOnHead(handle, firstPiece, lastPiece, window = START_WINDOW)

        // Diagnóstico del plan: sin estos números, "tarda en arrancar" no se
        // puede distinguir de "el rango de piezas está mal calculado".
        val filesCount = torrentInfo.numFiles()
        onDiagnostic?.invoke(
            "plan: archivo=$fileIndex/$filesCount " +
                "offset=${files.fileOffset(fileIndex)} bytes=${files.fileSize(fileIndex)} " +
                "pieza=${files.pieceLength()}B rango=$firstPiece..$lastPiece " +
                "head=${StreamPriorities.headPieces(firstPiece, lastPiece)}"
        )
    }

    /**
     * Deja descargable solo la ventana del inicio; el resto del archivo va a
     * IGNORE. Es lo que concentra los 178 peers en 8 piezas en vez de repartir
     * las peticiones por todo el archivo.
     */
    private fun concentrateOnHead(
        handle: TorrentHandle,
        firstPiece: Int,
        lastPiece: Int,
        window: Int
    ) {
        val allowed = StreamPriorities.headPieces(firstPiece, lastPiece, window)
        for (piece in firstPiece..lastPiece) {
            if (piece !in allowed) {
                runCatching { handle.piecePriority(piece, Priority.IGNORE) }
            }
        }
    }

    /** Abre el head completo y deja el resto del archivo en IGNORE. */
    private fun expandHead(handle: TorrentHandle, firstPiece: Int, lastPiece: Int) {
        concentrateOnHead(handle, firstPiece, lastPiece, window = StreamPriorities.HEAD_WINDOW)
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)
        onDiagnostic?.invoke("head abierto: ${StreamPriorities.HEAD_WINDOW} piezas")
    }

    /** ¿Están todas las piezas del head en disco? */
    private fun headIsComplete(handle: TorrentHandle, firstPiece: Int, lastPiece: Int): Boolean {
        val head = StreamPriorities.headPieces(firstPiece, lastPiece)
        if (head.isEmpty()) return true
        return head.all { runCatching { handle.havePiece(it) }.getOrDefault(false) }
    }

    /**
     * Devuelve el resto del archivo a la descarga, ya con el inicio en mano.
     *
     * `filePriority` recalcula las prioridades desde el archivo, así que borra
     * el SEVEN del head: se vuelve a aplicar después, con su deadline.
     */
    private fun releaseRestOfFile(
        handle: TorrentHandle,
        fileIndex: Int,
        firstPiece: Int,
        lastPiece: Int
    ) {
        runCatching { handle.filePriority(fileIndex, Priority.NORMAL) }
        StreamPriorities.boostHead(handle, firstPiece, lastPiece)
        onDiagnostic?.invoke("head listo: resto del archivo habilitado")
    }

    /**
     * Calcula el rango de piezas [first, last] que cubren un archivo.
     * jlibtorrent 2.x no expone pieceIndexAtFile/lastPieceIndexAtFile,
     * así que usamos aritmética simple con fileOffset/fileSize/pieceLength.
     */
    private fun pieceRangeForFile(files: FileStorage, fileIndex: Int): IntRange {
        val pieceLength = files.pieceLength().toLong()
        val fileOffset = files.fileOffset(fileIndex)
        val fileSize = files.fileSize(fileIndex)
        val firstPiece = (fileOffset / pieceLength).toInt()
        val lastPiece = ((fileOffset + fileSize - 1) / pieceLength).toInt()
        return firstPiece..lastPiece
    }

    /**
     * Detecta los subtítulos sueltos del torrent y los deja listos para servir.
     *
     * `prepareDownload` deja todo lo que no es el video en IGNORE; acá esos
     * archivos vuelven a `DEFAULT` para que se bajen. Son de pocos KB, así que
     * llegan antes de que el usuario abra el menú de pistas.
     *
     * Las etiquetas repetidas se numeran: dos `.srt` en español sin más datos se
     * verían idénticos en el menú, y elegir el equivocado es no ver nada.
     */
    private fun prepareSubtitles(
        handle: TorrentHandle,
        torrentInfo: TorrentInfo,
        videoIndex: Int,
        sessionDir: File
    ): List<PreparedSubtitle> {
        val files = torrentInfo.files()
        val seenLabels = mutableMapOf<String, Int>()
        val result = mutableListOf<PreparedSubtitle>()

        for (index in 0 until torrentInfo.numFiles()) {
            if (index == videoIndex) continue
            val name = runCatching { files.fileName(index) }.getOrNull() ?: continue
            if (!TorrentSubtitles.isSubtitle(name)) continue

            val path = TorrentPaths.resolveWithin(sessionDir, files.filePath(index)) ?: continue
            val size = files.fileSize(index)
            if (size <= 0L) continue

            runCatching { handle.filePriority(index, Priority.NORMAL) }

            val baseLabel = TorrentSubtitles.labelOf(name)
            val repeated = (seenLabels[baseLabel] ?: 0) + 1
            seenLabels[baseLabel] = repeated
            val label = if (repeated == 1) baseLabel else "$baseLabel ($repeated)"

            result += PreparedSubtitle(
                fileName = name,
                label = label,
                language = TorrentSubtitles.languageOf(name),
                contentType = TorrentSubtitles.contentTypeOf(name),
                sidecar = SidecarFile(
                    file = path,
                    fileOffsetInTorrent = files.fileOffset(index),
                    fileSize = size,
                    contentType = TorrentSubtitles.contentTypeOf(name)
                )
            )

            if (result.size >= TorrentSubtitles.MAX_SUBTITLES) break
        }

        return result
    }

    private fun emitProgress(handle: TorrentHandle): TorrentStreamState.Progress? = try {
        val status = handle.status()
        val progress = TorrentStreamState.Progress(
            percent = (status.progress() * 100f).coerceIn(0f, 100f),
            downloadRate = status.downloadRate().toLong(),
            peers = status.numPeers(),
            state = status.state().name
        )

        // Durante la descarga también interesa ver el tráfico de salida: si el
        // motor no devuelve nada, los peers dejan de darnos piezas ("choking"
        // recíproco) y el video se arrastra. Medido en el KM6: con subida
        // cortada, la bajada cayó de ~600 KB/s a ~70 KB/s.
        val now = System.currentTimeMillis()
        if (now - lastRateDiagnostic >= DIAGNOSTIC_INTERVAL_MS) {
            lastRateDiagnostic = now
            onDiagnostic?.invoke(
                "descarga: ${status.downloadRate() / 1024} KB/s subida: " +
                    "${status.uploadRate() / 1024} KB/s peers=${status.numPeers()} " +
                    "progreso=${(status.progress() * 100).toInt()}% estado=${status.state().name}"
            )
        }

        progress
    } catch (error: Exception) {
        null
    }

    /** Subtítulo detectado, antes de tener la URL del servidor. */
    private data class PreparedSubtitle(
        val fileName: String,
        val label: String,
        val language: String?,
        val contentType: String,
        val sidecar: SidecarFile
    )

    private companion object {
        /**
         * Piezas que se dejan descargables al arrancar. Con 1, los 178 peers
         * apuntan a la misma pieza y el inicio llega en segundos; medido en el
         * KM6: con el head completo (8) competían 8 piezas a la vez y la
         * primera tardó 24 s.
         */
        const val START_WINDOW = 1

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
