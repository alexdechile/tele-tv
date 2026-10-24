package com.tele.app.core.torrent

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Servidor HTTP mínimo, atado a loopback, que sirve **un** archivo del torrent a
 * ExoPlayer.
 *
 * ExoPlayer no sabe que hay un torrent: pide rangos (`Range: bytes=a-b`) como a
 * cualquier servidor y este servidor espera a que libtorrent tenga las piezas
 * necesarias antes de responder. Soporta:
 *  - `GET` y `HEAD`
 *  - `Range: bytes=start-end`, `bytes=start-` y sufijo `bytes=-N`
 *    (crítico para MP4 con el átomo `moov` al final)
 *  - respuestas `206 Partial Content` con `Content-Range`
 */
class HttpStreamServer(
    private val pieceLength: Long,
    private val numPieces: Int,
    private val file: File,
    private val fileOffsetInTorrent: Long,
    private val fileSize: Long,
    private val contentType: String,
    private val pieces: PieceAvailability,
    private val pieceTimeoutMs: Long = DEFAULT_PIECE_TIMEOUT_MS
) {

    private val serverSocket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(true)

    val url: String
        get() = "http://$LOOPBACK:$port/stream"

    private val port: Int
        get() = serverSocket.localPort

    fun start() {
        executor.execute {
            while (running.get()) {
                val socket = try {
                    serverSocket.accept()
                } catch (error: IOException) {
                    break // socket cerrado en stop()
                }
                executor.execute { handleConnection(socket) }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }

    // ---------------------------------------------------------------------
    // Protocolo HTTP
    // ---------------------------------------------------------------------

    private fun handleConnection(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = SOCKET_TIMEOUT_MS
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val rangeHeader = readHeaders(reader)

                val isHead = requestLine.startsWith("HEAD", ignoreCase = true)
                val range = parseRange(rangeHeader, fileSize)
                val output = client.getOutputStream()

                if (range == null) {
                    writeSimpleResponse(output, "416 Range Not Satisfiable")
                    return
                }

                writeHeaders(output, range)
                if (isHead) return

                streamRange(output, range)
                output.flush()
            } catch (error: Exception) {
                // Cliente que se va, timeout de pieza, etc. No es fatal: la
                // conexión se cierra y ExoPlayer reintenta si hace falta.
            }
        }
    }

    private fun readHeaders(reader: java.io.BufferedReader): String? {
        var rangeHeader: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) {
                rangeHeader = line.substringAfter(':').trim()
            }
        }
        return rangeHeader
    }

    private fun writeHeaders(output: OutputStream, range: ByteRange) {
        val header = if (range.partial) {
            "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Range: bytes ${range.start}-${range.end}/$fileSize\r\n"
        } else {
            "HTTP/1.1 200 OK\r\n"
        }
        val payload = header +
            "Content-Length: ${range.length}\r\n" +
            "Content-Type: $contentType\r\n" +
            "Accept-Ranges: bytes\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        output.write(payload.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    private fun writeSimpleResponse(output: OutputStream, status: String) {
        val payload = "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(payload.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    // ---------------------------------------------------------------------
    // Streaming con espera de piezas
    // ---------------------------------------------------------------------

    private fun streamRange(output: OutputStream, range: ByteRange) {
        val lastPiece = (numPieces - 1).coerceAtLeast(0)

        awaitFileOnDisk()

        RandomAccessFile(file, "r").use { raf ->
            var position = range.start
            val buffer = ByteArray(BUFFER_SIZE)

            while (position <= range.end && running.get()) {
                val absoluteOffset = fileOffsetInTorrent + position
                val piece = (absoluteOffset / pieceLength).toInt().coerceIn(0, lastPiece)

                if (!awaitPiece(piece, lastPiece)) {
                    throw IOException("Timeout esperando la pieza $piece")
                }

                val offsetInPiece = absoluteOffset % pieceLength
                val remainingInPiece = pieceLength - offsetInPiece
                val remainingRange = range.end - position + 1
                val toRead = minOf(remainingRange, remainingInPiece, buffer.size.toLong()).toInt()
                if (toRead <= 0) break

                raf.seek(position)
                raf.readFully(buffer, 0, toRead)
                output.write(buffer, 0, toRead)
                output.flush()

                position += toRead
            }
        }
    }

    /** Espera a que libtorrent tenga la pieza, priorizando lo que ExoPlayer pide. */
    private fun awaitPiece(piece: Int, lastPiece: Int): Boolean {
        val deadline = System.currentTimeMillis() + pieceTimeoutMs

        while (System.currentTimeMillis() < deadline) {
            if (!running.get()) return false
            if (pieces.hasPiece(piece)) return true
            prioritizeAround(piece, lastPiece)
            Thread.sleep(POLL_INTERVAL_MS)
        }

        return pieces.hasPiece(piece)
    }

    /**
     * Sube la prioridad de la pieza pedida y de las siguientes. Esto es lo que
     * evita que un seek al final del video (moov/índice) espere a que termine
     * la descarga secuencial desde el principio.
     */
    private fun prioritizeAround(piece: Int, lastPiece: Int) {
        pieces.prioritize(piece)
        for (next in piece + 1..piece + PRIORITY_LOOKAHEAD) {
            if (next > lastPiece) break
            pieces.prioritize(next)
        }
    }

    private fun awaitFileOnDisk() {
        val deadline = System.currentTimeMillis() + FILE_APPEAR_TIMEOUT_MS
        while (!file.exists() && System.currentTimeMillis() < deadline && running.get()) {
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    // ---------------------------------------------------------------------
    // Range
    // ---------------------------------------------------------------------

    private fun parseRange(header: String?, total: Long): ByteRange? {
        if (total <= 0) return null
        if (header.isNullOrBlank()) return ByteRange(0, total - 1, partial = false)

        val spec = header.removePrefix("bytes=").trim()
        val parts = spec.split('-', limit = 2)
        if (parts.size != 2) return null

        val startRaw = parts[0].trim()
        val endRaw = parts[1].trim()

        return when {
            // bytes=-N  → los últimos N bytes
            startRaw.isEmpty() && endRaw.isNotEmpty() -> {
                val suffix = endRaw.toLongOrNull() ?: return null
                if (suffix <= 0) return null
                val start = (total - suffix).coerceAtLeast(0)
                ByteRange(start, total - 1, partial = true)
            }

            startRaw.isNotEmpty() -> {
                val start = startRaw.toLongOrNull() ?: return null
                val end = if (endRaw.isEmpty()) total - 1 else (endRaw.toLongOrNull() ?: return null)
                if (start > end || start >= total) return null
                ByteRange(start, minOf(end, total - 1), partial = true)
            }

            else -> null
        }
    }

    private data class ByteRange(val start: Long, val end: Long, val partial: Boolean) {
        val length: Long get() = end - start + 1
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val BACKLOG = 16
        const val BUFFER_SIZE = 512 * 1024
        const val POLL_INTERVAL_MS = 100L
        const val DEFAULT_PIECE_TIMEOUT_MS = 90_000L
        const val FILE_APPEAR_TIMEOUT_MS = 60_000L
        const val SOCKET_TIMEOUT_MS = 120_000
        const val PRIORITY_LOOKAHEAD = 8
    }
}
