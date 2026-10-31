package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La política de arranque, sin libtorrent de por medio.
 *
 * Lo que se protege acá es concreto: si la ventana del inicio no se aplica, el
 * video se queda en negro hasta casi el 100 % de la descarga (medido en el KM6:
 * a 81 % el archivo no tenía los primeros ~8 %). Estas pruebas fallan si alguien
 * vuelve a dejar la ventana vacía, corrida o sin deadline.
 */
class StreamPrioritiesTest {

    @Test
    fun `la ventana del inicio cubre las primeras piezas del archivo`() {
        assertEquals(0..7, StreamPriorities.headPieces(firstPiece = 0, lastPiece = 999))
    }

    @Test
    fun `la ventana se recorta al final del archivo`() {
        assertEquals(3..5, StreamPriorities.headPieces(firstPiece = 3, lastPiece = 5))
    }

    @Test
    fun `un archivo de una sola pieza pide esa pieza`() {
        assertEquals(7..7, StreamPriorities.headPieces(firstPiece = 7, lastPiece = 7))
    }

    @Test
    fun `indices imposibles no piden ninguna pieza`() {
        assertTrue(StreamPriorities.headPieces(firstPiece = 0, lastPiece = -1).isEmpty())
        assertTrue(StreamPriorities.headPieces(firstPiece = -1, lastPiece = 10).isEmpty())
        assertTrue(StreamPriorities.headPieces(firstPiece = 9, lastPiece = 3).isEmpty())
    }

    @Test
    fun `una ventana de cero no deja el inicio sin prioridad`() {
        assertEquals(0..0, StreamPriorities.headPieces(firstPiece = 0, lastPiece = 100, window = 0))
    }

    @Test
    fun `boostHead pone prioridad y deadline a cada pieza de la ventana`() {
        val prioridades = mutableListOf<Int>()
        val plazos = mutableListOf<Int>()

        StreamPriorities.boostHead(
            firstPiece = 0,
            lastPiece = 99,
            setPriority = { prioridades.add(it) },
            setDeadline = { plazos.add(it) }
        )

        assertEquals((0 until StreamPriorities.HEAD_WINDOW).toList(), prioridades)
        assertEquals("toda pieza priorizada necesita su deadline", prioridades, plazos)
    }

    @Test
    fun `boostHead no toca piezas fuera del archivo`() {
        val tocadas = mutableListOf<Int>()

        StreamPriorities.boostHead(
            firstPiece = 10,
            lastPiece = 12,
            window = 8,
            setPriority = { tocadas.add(it) },
            setDeadline = { }
        )

        assertEquals(listOf(10, 11, 12), tocadas)
    }
}
