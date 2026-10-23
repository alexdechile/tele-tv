package com.tele.app.core.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import libtorrent.addTorrentParams
import libtorrent.alert
import libtorrent.alert_category
import libtorrent.session
import libtorrent.session_configuration
import libtorrent.settings_pack
import libtorrent.storage_mode
import libtorrent.torrent_handle
import libtorrent.torrent_info
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer

/**
 * Motor de torrents basado en LibTorrent4j.
 *
 * Responsabilidades:
 * 1. Mantener una Sesión de libtorrent activa.
 * 2. Añadir un torrent por magnet URI e iniciar descarga secuencial (streaming).
 * 3. Exponer un servidor HTTP local ligero que sirve el archivo de vídeo a ExoPlayer
 *    a medida que se descarga (range requests).
 *
 * ExoPlayer NO sabe que hay un torrent; solo recibe http://127.0.0.1:PORT/stream
 */
class TorrentEngine @constructor(
    private val coroutineScope: CoroutineScope
) {

    private val session by lazy {
        session(session_configuration().apply {
            setListenPort(6881)
            setAlertMask(alert_category.all_categories.toInt())
        }).apply {
            // Configuración orientada a streaming secuencial
            val settings = settings_pack {
                sequentialDownload = true
                allowMultipleConnections = true
            }
            applySettingsPack(settings)
        }
    }

    /**
     * Inicia un torrent y emite la URL HTTP local que ExoPlayer debe reproducir.
     *
     * @param magnetUri URI del torrent (magnet:?xt=...)
     * @param fileIndex Índice del archivo de vídeo dentro del torrent
     * @return Flow con la URL local (un solo valor, se mantiene abierto mientras descarga)
     */
    fun startStream(magnetUri: String, fileIndex: Int): Flow<String> = flow {
        val params = addTorrentParams().apply {
            this.url = magnetUri
            savePath = "/data/user/0/com.tele.app.tv/cache/torrents"
            storageMode = storage_mode.storage_mode_sparse
        }

        val handle: torrent_handle = session.addTorrent(params)

        // Esperar metadatos (bloqueante en corrutina de fondo)
        while (!handle.hasMetadata()) {
            Thread.sleep(500)
        }

        val info: torrent_info = handle.torrentFile()
        val numFiles = info.numFiles()

        // Priorizar solo el archivo de vídeo (resto en 0)
        val priorities = IntArray(numFiles) { 0 }
        if (fileIndex in 0 until numFiles) {
            priorities[fileIndex] = 1
        }
        handle.prioritizeFiles(priorities)

        // Servidor HTTP local ligero
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/stream") { exchange ->
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val start = rangeHeader
                ?.substringAfter("bytes=")
                ?.substringBefore("-")
                ?.toIntOrNull() ?: 0

            val fileSize = handle.fileProgress(fileIndex).toInt()
            val end = (start + 1024 * 1024 - 1).coerceAtMost(fileSize - 1)

            if (start >= fileSize) {
                exchange.sendResponseHeaders(416, -1)
                exchange.close()
                return@createContext
            }

            val buffer = ByteArray(end - start + 1)
            handle.read(info.files().get(fileIndex), start, buffer.size, { _, bytesRead ->
                exchange.responseHeaders.add("Accept-Ranges", "bytes")
                exchange.sendResponseHeaders(206, bytesRead.toLong())
                exchange.responseBody.write(buffer.copyOfRange(0, bytesRead))
                exchange.responseBody.close()
            }, 0)
        }
        server.start()

        val localUrl = "http://127.0.0.1:${server.address.port}/stream"
        emit(localUrl)

        // Mantener el flujo vivo mientras el torrent esté activo
        coroutineScope.launch(Dispatchers.Default) {
            while (handle.status().state != libtorrent.torrent_status.state_seeding) {
                Thread.sleep(1000)
            }
        }
    }

    fun stop() {
        // TODO: pausar sesión y cerrar servidor HTTP cuando se implemente el ciclo de vida completo
    }
}
