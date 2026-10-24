package com.tele.app.core.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Pruebas del servidor HTTP local, ejecutadas en la JVM (sin libtorrent nativo
 * ni Android). Verifican lo que ExoPlayer necesita sí o sí: rangos, sufijos,
 * HEAD y el mapeo de offsets del archivo dentro del torrent.
 */
class HttpStreamServerTest {

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun tempFileWithSize(size: Int): Pair<File, ByteArray> {
        val bytes = Random(7).nextBytes(size)
        val file = File.createTempFile("tele-stream", ".mkv")
        file.deleteOnExit()
        file.writeBytes(bytes)
        return file to bytes
    }

    private fun startServer(
        file: File,
        size: Long,
        pieces: PieceAvailability,
        pieceTimeoutMs: Long = 5_000,
        fileOffsetInTorrent: Long = 0
    ): HttpStreamServer {
        val server = HttpStreamServer(
            pieceLength = PIECE_LENGTH,
            numPieces = ((size + PIECE_LENGTH - 1) / PIECE_LENGTH).toInt(),
            file = file,
            fileOffsetInTorrent = fileOffsetInTorrent,
            fileSize = size,
            contentType = "video/x-matroska",
            pieces = pieces,
            pieceTimeoutMs = pieceTimeoutMs
        )
        server.start()
        return server
    }

    private data class Response(val code: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun request(url: String, range: String? = null, head: Boolean = false): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = if (head) "HEAD" else "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 15_000
        range?.let { connection.setRequestProperty("Range", it) }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = if (head || stream == null) ByteArray(0) else stream.readBytes()
            // Claves en minusculas: headerFields() normaliza la capitalizacion
            // de forma que varia entre JVM, y no queremos tests fragiles.
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { (name, _) -> name.lowercase() }
                .mapValues { (_, values) -> values?.firstOrNull().orEmpty() }
            Response(code, headers, body)
        } finally {
            connection.disconnect()
        }
    }

    private val allPiecesAvailable = object : PieceAvailability {
        override fun hasPiece(index: Int) = true
        override fun prioritize(index: Int) = Unit
    }

    // -----------------------------------------------------------------
    // Casos
    // -----------------------------------------------------------------

    @Test
    fun `sirve el archivo completo con 200`() {
        val (file, bytes) = tempFileWithSize(50_000)
        val server = startServer(file, bytes.size.toLong(), allPiecesAvailable)
        try {
            val response = request(server.url)

            assertEquals(200, response.code)
            assertEquals(bytes.size.toString(), response.headers["content-length"])
            assertArrayEquals(bytes, response.body)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `responde 206 con Content-Range para un rango explicito`() {
        val (file, bytes) = tempFileWithSize(50_000)
        val server = startServer(file, bytes.size.toLong(), allPiecesAvailable)
        try {
            val response = request(server.url, range = "bytes=1000-1999")

            assertEquals(206, response.code)
            assertEquals("bytes 1000-1999/50000", response.headers["content-range"])
            assertEquals("1000", response.headers["content-length"])
            assertArrayEquals(bytes.copyOfRange(1000, 2000), response.body)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `soporta rango abierto y sufijo -N`() {
        val (file, bytes) = tempFileWithSize(30_000)
        val server = startServer(file, bytes.size.toLong(), allPiecesAvailable)
        try {
            // bytes=29000-  (hasta el final)
            val open = request(server.url, range = "bytes=29000-")
            assertEquals(206, open.code)
            assertArrayEquals(bytes.copyOfRange(29_000, 30_000), open.body)

            // bytes=-500  (los ultimos 500: lo que pide ExoPlayer para el moov al final)
            val suffix = request(server.url, range = "bytes=-500")
            assertEquals(206, suffix.code)
            assertEquals("bytes 29500-29999/30000", suffix.headers["content-range"])
            assertArrayEquals(bytes.copyOfRange(29_500, 30_000), suffix.body)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `HEAD devuelve cabeceras sin cuerpo`() {
        val (file, bytes) = tempFileWithSize(10_000)
        val server = startServer(file, bytes.size.toLong(), allPiecesAvailable)
        try {
            val response = request(server.url, head = true)

            assertEquals(200, response.code)
            assertEquals("10000", response.headers["content-length"])
            assertEquals("bytes", response.headers["accept-ranges"])
            assertEquals(0, response.body.size)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `rango fuera del archivo devuelve 416`() {
        val (file, bytes) = tempFileWithSize(10_000)
        val server = startServer(file, bytes.size.toLong(), allPiecesAvailable)
        try {
            val response = request(server.url, range = "bytes=50000-")
            assertEquals(416, response.code)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `espera a que la pieza aparezca antes de responder`() {
        val (file, bytes) = tempFileWithSize(40_000)
        val startedAt = System.currentTimeMillis()

        // Simula descarga en curso: las piezas aparecen recien a los 300 ms.
        val slowPieces = object : PieceAvailability {
            override fun hasPiece(index: Int) = System.currentTimeMillis() - startedAt > 300
            override fun prioritize(index: Int) = Unit
        }

        val server = startServer(file, bytes.size.toLong(), slowPieces)
        try {
            val response = request(server.url, range = "bytes=0-1023")

            assertEquals(206, response.code)
            assertArrayEquals(bytes.copyOfRange(0, 1024), response.body)
            assertTrue(
                "deberia haber esperado a la pieza",
                System.currentTimeMillis() - startedAt >= 300
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `mapea el offset del archivo dentro del torrent`() {
        val (file, bytes) = tempFileWithSize(4_096)
        val fileOffsetInTorrent = 5_000L

        // Solo las piezas que cubren ese archivo en el espacio del torrent
        // estan disponibles. Si el mapeo de offset estuviera mal, el servidor
        // pediria piezas del principio y se quedaria esperando.
        val firstPiece = (fileOffsetInTorrent / PIECE_LENGTH).toInt()
        val lastPiece = ((fileOffsetInTorrent + bytes.size - 1) / PIECE_LENGTH).toInt()
        val offsetPieces = object : PieceAvailability {
            override fun hasPiece(index: Int) = index in firstPiece..lastPiece
            override fun prioritize(index: Int) = Unit
        }

        val server = startServer(
            file = file,
            size = bytes.size.toLong(),
            pieces = offsetPieces,
            pieceTimeoutMs = 2_000,
            fileOffsetInTorrent = fileOffsetInTorrent
        )
        try {
            val response = request(server.url)

            assertEquals(200, response.code)
            assertArrayEquals(bytes, response.body)
        } finally {
            server.stop()
        }
    }

    private companion object {
        const val PIECE_LENGTH = 16_384L
    }
}
