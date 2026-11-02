package com.tele.app.core.torrent

import java.io.File

/**
 * Archivo que acompaña al video dentro del torrent y que el servidor local
 * también sirve (hoy: subtítulos sueltos).
 *
 * Se guarda el offset dentro del torrent porque es lo que permite esperar la
 * pieza correcta: los subtítulos empiezan en el byte que libtorrent diga, no en
 * el 0 del archivo.
 */
data class SidecarFile(
    val file: File,
    val fileOffsetInTorrent: Long,
    val fileSize: Long,
    val contentType: String
)
