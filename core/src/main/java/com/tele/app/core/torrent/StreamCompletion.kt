package com.tele.app.core.torrent

/**
 * Cuándo la descarga ya no tiene nada más que pedir y la subida debe cortarse.
 *
 * **El problema que resuelve:** el motor no paraba nunca. Al terminar de bajar
 * el archivo el torrent seguía en la sesión, anunciándose en los trackers y
 * repartiendo piezas a los peers hasta que el usuario saliera del reproductor.
 * Con el tope de 32 KB/s no repartía material útil, pero la TV quedaba
 * "presente" en el enjambre durante toda la película.
 *
 * **La señal correcta no es `status.progress()`:** ese número promedia el
 * torrent completo, y los demás archivos van en `Priority.IGNORE`, así que
 * puede no llegar nunca a 100 %. Tampoco existe `progressWanted()` en
 * jlibtorrent 2.0.15.3 (la API de libtorrent que expone esa métrica no está
 * envuelta acá). Lo que sí hay es `TorrentHandle.fileProgress()`, que devuelve
 * los bytes bajados **por archivo**: cuando igualan el tamaño del archivo
 * elegido, el video está entero en disco.
 *
 * Pausar es suficiente y no rompe nada: [HttpStreamServer] sirve el archivo
 * desde el disco, no se lo pide a la sesión, así que ExoPlayer sigue haciendo
 * seek igual. Pausar además suelta los peers y detiene los anuncios, que es
 * justo lo que se buscaba.
 */
object StreamCompletion {

    /**
     * ¿El archivo ya está completo? Un tamaño en cero no es "completo": es un
     * archivo que no se puede evaluar, y pausar por eso cortaría la descarga
     * buena.
     */
    fun isFileComplete(downloadedBytes: Long, fileSize: Long): Boolean =
        fileSize > 0L && downloadedBytes >= fileSize

    /**
     * Pausa el torrent si el archivo elegido ya está entero.
     *
     * Devuelve `true` solo cuando efectivamente pausó, para que quien llama no
     * repita la operación en cada vuelta del bucle de progreso.
     *
     * @param fileProgress bytes bajados por archivo (`null` si libtorrent no
     *   los pudo entregar en ese instante).
     * @param pause acción que pausa el torrent.
     */
    fun pauseIfComplete(
        fileIndex: Int,
        fileSize: Long,
        fileProgress: () -> LongArray?,
        pause: () -> Unit
    ): Boolean {
        val downloaded = fileProgress()?.getOrNull(fileIndex) ?: return false
        if (!isFileComplete(downloaded, fileSize)) return false
        return runCatching { pause() }.isSuccess
    }
}
