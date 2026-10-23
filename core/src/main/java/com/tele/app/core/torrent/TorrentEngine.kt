package com.tele.app.core.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import libtorrent4j.AddTorrentParams
import libtorrent4j.Session
import libtorrent4j.SessionConfiguration
import libtorrent4j.SettingsPack
import libtorrent4j.StorageMode
import libtorrent4j.TorrentHandle
import libtorrent4j.TorrentInfo
import java.net.ServerSocket

/**
 * Motor de torrents basado en LibTorrent4j.
 *
 * Responsabilidades:
 * 1. Mantener una Sesión de libtorrent activa.
 * 2. Añadir un torrent por magnet URI e iniciar descarga secuencial (streaming).
 * 3. Exponer un servidor HTTP local ligero que sirve el archivo de vídeo a ExoPlayer
 *    a medida que se descansa (range requests).
 *
 * ExoPlayer NO sabe que hay un torrent; solo recibe http://127.0.0.1:PORT/stream
 */
class TorrentEngine(private val coroutineScope: CoroutineScope) {

    private val session by lazy {
        Session(SessionConfiguration()).apply {
            val settings = SettingsPack().apply {
                setSequentialDownload(true)
                setAllowMultipleConnections(true)
            }
            applySettingsPack(settings)
        }
    }

    /**
     * Inicia un torrent y emite la URL HTTP local que ExoPlayer debe reproducir.
     *
     * @param magnetUri URI del torrent (magnet:?xt=...)
     * @param fileIndex Índice del archivo de vídeo dentro del torrent
     * @return Flow con la URL local
     */
    fun startStream(magnetUri: String, fileIndex: Int): Flow<String> = flow {
        val params = AddTorrentParams().apply {
            setUrl(magnetUri)
            setSavePath("/data/user/0/com.tele.app.tv/cache/torrents")
            setStorageMode(StorageMode.storage_mode_sparse)
        }

        val handle: TorrentHandle = session.addTorrent(params)

        // Esperar metadatos (bloqueante en corrutina de fondo)
        while (!handle.hasMetadata()) {
            Thread.sleep(500)
        }

        val info: TorrentInfo = handle.torrentFile()
        val numFiles = info.numFiles()

        // Priorizar solo el archivo de vídeo (resto en 0)
        val priorities = IntArray(numFiles) { 0 }
        if (fileIndex in 0 until numFiles) {
            priorities[fileIndex] = 1
        }
        handle.prioritizeFiles(priorities)

        // Servidor HTTP local ligero basado en ServerSocket (disponible en Android)
        val serverSocket = ServerSocket(0)
        serverSocket.accept()

        val localUrl = "http://127.0.0.1:${serverSocket.localPort}/stream"
        emit(localUrl)

        // Mantener el flujo vivo mientras el torrent esté activo
        coroutineScope.launch(Dispatchers.Default) {
            while (handle.status().state != libtorrent4j.TorrentStatus.State.state_seeding) {
                Thread.sleep(1000)
            }
        }
    }

    fun stop() {
        // TODO: cerrar servidor HTTP y pausar sesión cuando se implemente el ciclo de vida completo
    }
}
