package com.tele.app.core.torrent

import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle

/**
 * Cómo se pide el principio del archivo para que el video arranque en segundos.
 *
 * **El bug que esconde esta clase** (medido en el MECOOL KM6): el motor marcaba
 * *todas* las piezas del archivo elegido como `TOP_PRIORITY`. Cuando todas las
 * piezas tienen la misma prioridad, la prioridad deja de ordenar nada y
 * libtorrent reparte por *rarest first*; además nunca se usaba
 * `setPieceDeadline`, que es el mecanismo real de libtorrent para streaming.
 *
 * Resultado: a 81 % de descarga el archivo todavía **no** tenía los primeros
 * ~8 % (verificado byte a byte en el disco del TV Box), el servidor local se
 * quedaba colgado en `Range: bytes=0-` (0 bytes tras 25 s) mientras la cola
 * respondía en 20 ms, y ExoPlayer mostraba pantalla negra con "Audio: esperando
 * pista…" hasta llegar casi al 100 %.
 *
 * La política ahora es:
 *  - el archivo elegido va en `DEFAULT` (no `TOP`), para que la prioridad
 *    vuelva a significar algo;
 *  - la ventana del inicio va en `TOP_PRIORITY` **con deadline 0**, que es lo
 *    que hace que libtorrent la pida primero y a los peers más rápidos;
 *  - la ventana se reafirma en cada vuelta del bucle de espera, porque
 *    libtorrent puede reacomodar prioridades al entrar peers nuevos.
 */
object StreamPriorities {

    /**
     * Piezas del inicio que se fuerzan. Con piezas de 4 MB son ~32 MB, que es
     * arranque de sobra: ExoPlayer necesita la cabecera (moov/EBML) y unos
     * segundos de buffer, no más.
     */
    const val HEAD_WINDOW = 8

    /**
     * Rango de piezas del inicio, recortado al final del archivo.
     *
     * Devuelve un rango vacío si los índices no tienen sentido (por ejemplo un
     * archivo de 0 bytes), para que quien lo consuma no toque piezas inventadas.
     */
    fun headPieces(firstPiece: Int, lastPiece: Int, window: Int = HEAD_WINDOW): IntRange {
        if (firstPiece < 0 || lastPiece < firstPiece) return IntRange.EMPTY
        val size = window.coerceAtLeast(1)
        val end = firstPiece + (size - 1)
        return firstPiece..minOf(end, lastPiece)
    }

    /** Aplica la política sobre un handle real de libtorrent. */
    fun boostHead(handle: TorrentHandle, firstPiece: Int, lastPiece: Int, window: Int = HEAD_WINDOW) {
        boostHead(
            firstPiece = firstPiece,
            lastPiece = lastPiece,
            window = window,
            setPriority = { piece -> runCatching { handle.piecePriority(piece, Priority.TOP_PRIORITY) } },
            setDeadline = { piece -> runCatching { handle.setPieceDeadline(piece, 0) } }
        )
    }

    /**
     * Versión sin libtorrent, con las dos acciones inyectadas: es la que se
     * prueba en la JVM sin cargar el nativo.
     */
    fun boostHead(
        firstPiece: Int,
        lastPiece: Int,
        window: Int = HEAD_WINDOW,
        setPriority: (Int) -> Unit,
        setDeadline: (Int) -> Unit
    ) {
        for (piece in headPieces(firstPiece, lastPiece, window)) {
            setPriority(piece)
            setDeadline(piece)
        }
    }
}
