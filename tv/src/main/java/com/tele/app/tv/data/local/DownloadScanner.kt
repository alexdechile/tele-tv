package com.tele.app.tv.data.local

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

/**
 * Representa un archivo de video descargado y conservado en almacenamiento.
 */
data class DownloadedFile(
    val file: File,
    val name: String,
    val cleanTitle: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val lastModified: Long,
    val formattedDate: String,
    val enclosingDir: File?
)

/**
 * Escanea y administra los archivos descargados y conservados en el TV Box.
 */
object DownloadScanner {
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "ts", "mov", "m4v")

    fun scan(context: Context): List<DownloadedFile> {
        val results = mutableListOf<DownloadedFile>()
        val seenPaths = mutableSetOf<String>()

        // 1. Directorio permanente de descargas
        val descargasDir = File(context.filesDir, "descargas")
        if (descargasDir.exists()) {
            descargasDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS && file.length() > 500_000L) {
                    val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    if (seenPaths.add(canonical)) {
                        results.add(toDownloadedFile(file, enclosingDir = null))
                    }
                }
            }
        }

        // 2. Sesiones conservadas en cache/torrents
        val torrentsDir = File(context.cacheDir, "torrents")
        if (torrentsDir.exists()) {
            torrentsDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.isDirectory) {
                    sessionDir.walkTopDown().forEach { file ->
                        if (file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS && file.length() > 500_000L) {
                            val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                            if (seenPaths.add(canonical)) {
                                results.add(toDownloadedFile(file, enclosingDir = sessionDir))
                            }
                        }
                    }
                }
            }
        }

        return results.sortedByDescending { it.lastModified }
    }

    private fun toDownloadedFile(file: File, enclosingDir: File?): DownloadedFile {
        val size = file.length()
        val formattedSize = formatFileSize(size)
        val cleanTitle = cleanVideoTitle(file.name)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(file.lastModified()))

        return DownloadedFile(
            file = file,
            name = file.name,
            cleanTitle = cleanTitle,
            sizeBytes = size,
            formattedSize = formattedSize,
            lastModified = file.lastModified(),
            formattedDate = formattedDate,
            enclosingDir = enclosingDir
        )
    }

    fun cleanVideoTitle(fileName: String): String {
        val withoutExt = fileName.substringBeforeLast('.')
        val spaced = withoutExt.replace('.', ' ').replace('_', ' ')
        val cleaned = spaced.replace(
            Regex("""(?i)\b(1080p|720p|2160p|4k|web-?dl|webrip|bluray|x264|x265|hevc|ddp5\.?1|aac|amzn|h264|korsub|subbed|hc)\b.*"""),
            ""
        ).trim()
        return cleaned.ifBlank { withoutExt }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${round(kb * 10) / 10.0} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${round(mb * 10) / 10.0} MB"
        val gb = mb / 1024.0
        return "${round(gb * 100) / 100.0} GB"
    }

    fun deleteDownload(item: DownloadedFile): Boolean {
        return try {
            if (item.enclosingDir != null && item.enclosingDir.exists()) {
                item.enclosingDir.deleteRecursively()
            } else {
                item.file.delete()
            }
        } catch (_: Exception) {
            false
        }
    }
}
