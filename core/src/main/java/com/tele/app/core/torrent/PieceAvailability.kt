package com.tele.app.core.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle

/**
 * Abstracción de "qué piezas del torrent ya están en disco".
 *
 * Existe para que [HttpStreamServer] se pueda probar en la JVM, sin cargar el
 * binario nativo de libtorrent ni necesitar un torrent real.
 */
interface PieceAvailability {
    fun hasPiece(index: Int): Boolean
    fun prioritize(index: Int)
}

/** Implementación real sobre el handle de libtorrent. */
class TorrentPieces(private val handle: TorrentHandle) : PieceAvailability {

    override fun hasPiece(index: Int): Boolean = try {
        handle.havePiece(index)
    } catch (error: Exception) {
        false
    }

    override fun prioritize(index: Int) {
        runCatching { handle.piecePriority(index, Priority.TOP_PRIORITY) }
    }
}
