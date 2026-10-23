package com.tele.app.core.torrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Motor de torrents basado en LibTorrent4j.
 *
 * Esta es una implementación inicial (stub) que compila sin dependencias
 * nativas específicas por plataforma. La integración real con LibTorrent4j
 * requiere añadir artefactos como:
 *   org.libtorrent4j:libtorrent4j-android-arm64-v8a
 *   org.libtorrent4j:libtorrent4j-android-armeabi-v7a
 *   org.libtorrent4j:libtorrent4j-android-x86
 *   org.libtorrent4j:libtorrent4j-android-x86_64
 *
 * Responsabilidades (pendiente de implementar):
 * 1. Mantener una Sesión de libtorrent activa.
 * 2. Añadir un torrent por magnet URI e iniciar descarga secuencial (streaming).
 * 3. Exponer un servidor HTTP local ligero que sirve el archivo de vídeo a ExoPlayer.
 *
 * ExoPlayer NO sabe que hay un torrent; solo recibe http://127.0.0.1:PORT/stream
 */
class TorrentEngine(private val coroutineScope: CoroutineScope) {

    /**
     * Inicia un torrent y emite la URL HTTP local que ExoPlayer debe reproducir.
     *
     * @param magnetUri URI del torrent (magnet:?xt=...)
     * @param fileIndex Índice del archivo de vídeo dentro del torrent
     * @return Flow con la URL local (placeholder hasta integrar LibTorrent4j nativo)
     */
    fun startStream(magnetUri: String, fileIndex: Int): Flow<String> = flow {
        emit("http://127.0.0.1:0/stream")
    }

    fun stop() {
        // TODO: cerrar servidor HTTP y pausar sesión cuando se implemente el ciclo de vida completo
    }
}
