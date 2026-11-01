package com.tele.app.core.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuándo se corta la subida, sin libtorrent de por medio.
 *
 * Lo que se protege: la app no puede quedarse sembrando después de que el video
 * terminó de bajar. Pero tampoco puede pausar antes de tiempo, porque pausar
 * corta también la descarga y el reproductor se queda sin datos.
 */
class StreamCompletionTest {

    @Test
    fun `un archivo a medio bajar no esta completo`() {
        assertFalse(StreamCompletion.isFileComplete(500L, 1_000L))
        assertFalse(StreamCompletion.isFileComplete(0L, 1_000L))
    }

    @Test
    fun `el archivo completo al byte esta completo`() {
        assertTrue(StreamCompletion.isFileComplete(1_000L, 1_000L))
    }

    @Test
    fun `bajar de mas no rompe la cuenta`() {
        assertTrue(StreamCompletion.isFileComplete(1_001L, 1_000L))
    }

    @Test
    fun `un tamano cero no es un archivo completo`() {
        // Pausar por un tamaño desconocido cortaría una descarga buena.
        assertFalse(StreamCompletion.isFileComplete(0L, 0L))
        assertFalse(StreamCompletion.isFileComplete(10L, 0L))
    }

    @Test
    fun `pausa cuando el archivo elegido llego completo`() {
        var pausado = false

        val result = StreamCompletion.pauseIfComplete(
            fileIndex = 1,
            fileSize = 300L,
            fileProgress = { longArrayOf(9_999L, 300L, 0L) },
            pause = { pausado = true }
        )

        assertTrue("tenía que pausar: el archivo 1 está completo", result)
        assertTrue(pausado)
    }

    @Test
    fun `mira el archivo elegido y no los demas`() {
        // El archivo 0 está con prioridad IGNORE: sus bytes no cuentan.
        var pausado = false

        val result = StreamCompletion.pauseIfComplete(
            fileIndex = 1,
            fileSize = 300L,
            fileProgress = { longArrayOf(300L, 120L) },
            pause = { pausado = true }
        )

        assertFalse(result)
        assertFalse(pausado)
    }

    @Test
    fun `sin datos de progreso no se pausa`() {
        var pausado = false

        val result = StreamCompletion.pauseIfComplete(
            fileIndex = 0,
            fileSize = 300L,
            fileProgress = { null },
            pause = { pausado = true }
        )

        assertFalse(result)
        assertFalse(pausado)
    }

    @Test
    fun `un indice fuera de rango no revienta ni pausa`() {
        var pausado = false

        val result = StreamCompletion.pauseIfComplete(
            fileIndex = 5,
            fileSize = 300L,
            fileProgress = { longArrayOf(300L) },
            pause = { pausado = true }
        )

        assertFalse(result)
        assertFalse(pausado)
    }

    @Test
    fun `si pausar falla se avisa que no se pauso`() {
        val result = StreamCompletion.pauseIfComplete(
            fileIndex = 0,
            fileSize = 300L,
            fileProgress = { longArrayOf(300L) },
            pause = { throw IllegalStateException("handle inválido") }
        )

        assertFalse("si el handle falló, hay que reintentar en la próxima vuelta", result)
    }

    @Test
    fun `el progreso que importa es el del archivo, no el del torrent`() {
        // Reproduce el caso real: torrent con extras en IGNORE. El promedio del
        // torrent nunca llega a 100 %, pero el archivo elegido sí.
        val fileSize = 4_000L
        val bytes = longArrayOf(0L, fileSize)

        assertTrue(StreamCompletion.isFileComplete(bytes[1], fileSize))
        assertFalse(
            "el total del torrent nunca da 100 % con archivos en IGNORE",
            StreamCompletion.isFileComplete(bytes.sum(), fileSize * 2)
        )
    }
}
