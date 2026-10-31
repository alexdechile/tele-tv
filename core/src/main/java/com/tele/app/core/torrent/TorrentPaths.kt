package com.tele.app.core.torrent

import java.io.File

/**
 * Resolución segura de rutas de archivo que vienen **de afuera**.
 *
 * El nombre del archivo dentro de un torrent no lo escribe la app: lo trae el
 * `.torrent`/metadatos que llegan del enjambre, o sea que es dato de un
 * desconocido. Si un torrent declara `../../shared_prefs/algo.xml` o una ruta
 * absoluta, `File(saveDir, ruta)` se escapa del directorio de descarga y
 * escribe/lee fuera de la zona de la app.
 *
 * Reglas que aplica [resolveWithin]:
 *  - nada de rutas absolutas (`/`, `C:\`, UNC `\\server`);
 *  - nada de segmentos `..`, `.` ni vacíos (dobles barras);
 *  - sin bytes nulos;
 *  - el resultado, ya canonicalizado (se resuelven symlinks), tiene que quedar
 *    dentro de [root]. Se compara con separador al final para que
 *    `/cache/torrents-otra` no pase como hijo de `/cache/torrents`.
 */
object TorrentPaths {

    /**
     * Devuelve el [File] dentro de [root] que corresponde a [relativePath], o
     * `null` si la ruta no es de fiar.
     */
    fun resolveWithin(root: File, relativePath: String): File? {
        val normalized = relativePath.trim()
        if (normalized.isEmpty()) return null
        if (normalized.indexOf('\u0000') >= 0) return null

        val unified = normalized.replace('\\', '/')
        if (unified.startsWith("/")) return null

        val segments = unified.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null

        // Windows: "C:algo" no es absoluto para File#isAbsolute, pero escapa igual.
        if (segments.first().contains(':')) return null

        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(rootCanonical, unified).canonicalFile }.getOrNull()
            ?: return null

        val rootPath = rootCanonical.path + File.separator
        return candidate.takeIf { it.path.startsWith(rootPath) }
    }

    /**
     * Borra el contenido de [root] (un nivel: subdirectorios completos y
     * archivos sueltos) y devuelve cuántos bytes se liberaron. No borra [root].
     */
    fun purge(root: File): Long {
        val children = root.listFiles() ?: return 0L
        var freed = 0L
        for (child in children) {
            freed += sizeOf(child)
            runCatching { child.deleteRecursively() }
        }
        return freed
    }

    /** Tamaño en bytes de [file], recursivo. 0 si no se puede leer. */
    fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return runCatching { file.length() }.getOrDefault(0L)
        val children = file.listFiles() ?: return 0L
        return children.sumOf { sizeOf(it) }
    }
}
