package com.tele.app.core.torrent

import org.libtorrent4j.FileStorage

/** Elección del archivo de video dentro de un torrent. */
object TorrentVideoFiles {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "m2ts", "flv", "wmv", "mpg", "mpeg"
    )

    fun isVideo(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    /**
     * Devuelve el índice del archivo a reproducir.
     *
     * Si el addon indicó un índice válido que además es video, se respeta.
     * Si no, se elige el archivo de video más grande del torrent (los releases
     * suelen traer .nfo, .srt y samples pequeños que no queremos).
     */
    fun pick(files: FileStorage, preferredIndex: Int?): Int? {
        val count = files.numFiles()
        if (count <= 0) return null

        if (preferredIndex != null && preferredIndex in 0 until count &&
            isVideo(files.fileName(preferredIndex))
        ) {
            return preferredIndex
        }

        var bestIndex = -1
        var bestSize = 0L
        for (index in 0 until count) {
            if (!isVideo(files.fileName(index))) continue
            val size = files.fileSize(index)
            if (size > bestSize) {
                bestSize = size
                bestIndex = index
            }
        }

        return bestIndex.takeIf { it >= 0 }
    }

    /** Content-Type aproximado según la extensión (ExoPlayer sniffea igual). */
    fun contentTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "mpg", "mpeg" -> "video/mpeg"
        else -> "video/mp4"
    }
}
