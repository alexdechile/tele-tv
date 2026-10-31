package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * La ruta del archivo dentro de un torrent viene del enjambre, no de la app.
 * Estos tests fijan el contrato: nada que se salga del directorio de la sesión
 * puede pasar, y lo que sí es legítimo tiene que resolverse bien.
 *
 * Corren en la JVM, sin el nativo de libtorrent.
 */
class TorrentPathsTest {

    private fun tempRoot(): File = Files.createTempDirectory("tele-paths").toFile()

    @Test
    fun `un nombre normal queda dentro`() {
        val root = tempRoot()
        val file = TorrentPaths.resolveWithin(root, "Pelicula.2019.1080p.mkv")

        assertNotNull("un nombre simple debe resolverse", file)
        assertEquals(File(root.canonicalFile, "Pelicula.2019.1080p.mkv"), file)
    }

    @Test
    fun `un subdirectorio del release queda dentro`() {
        val root = tempRoot()
        val file = TorrentPaths.resolveWithin(root, "Nombre.Del.Release/video.mkv")

        assertNotNull(file)
        assertTrue(
            "debe quedar bajo la raíz",
            file!!.canonicalPath.startsWith(root.canonicalPath + File.separator)
        )
    }

    @Test
    fun `no se permite subir con punto punto`() {
        val root = tempRoot()
        assertNull(TorrentPaths.resolveWithin(root, "../escape.txt"))
        assertNull(TorrentPaths.resolveWithin(root, "carpeta/../../escapa.txt"))
        assertNull(TorrentPaths.resolveWithin(root, "..\\escape.txt"))
    }

    @Test
    fun `no se permiten rutas absolutas`() {
        val root = tempRoot()
        assertNull(
            TorrentPaths.resolveWithin(root, "/data/data/com.tele.app.tv/shared_prefs/x.xml")
        )
        assertNull(TorrentPaths.resolveWithin(root, "\\Windows\\system32\\x.dll"))
    }

    @Test
    fun `no se permiten letras de unidad ni rutas vacias ni nulos`() {
        val root = tempRoot()
        assertNull(TorrentPaths.resolveWithin(root, "C:/windows/x.exe"))
        assertNull(TorrentPaths.resolveWithin(root, ""))
        assertNull(TorrentPaths.resolveWithin(root, "   "))
        assertNull(TorrentPaths.resolveWithin(root, "ok\u0000raro.mkv"))
    }

    @Test
    fun `un enlace simbolico que apunta afuera no se sigue`() {
        val root = tempRoot()
        val outside = Files.createTempDirectory("tele-fuera").toFile()
        File(outside, "secreto.txt").writeText("x")

        val link = File(root, "atajo")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            .isSuccess
        if (!created) return // sistema sin symlinks: no hay nada que probar

        assertNull(TorrentPaths.resolveWithin(root, "atajo/secreto.txt"))
    }

    @Test
    fun `purge borra el contenido y deja la raiz`() {
        val root = tempRoot()
        val sub = File(root, "session-abc").apply { mkdirs() }
        File(sub, "video.mkv").writeBytes(ByteArray(2048))
        File(root, "suelto.srt").writeText("hola")

        val freed = TorrentPaths.purge(root)

        assertEquals(2048L + 4L, freed)
        assertTrue("la raíz se conserva", root.exists())
        assertTrue("no debe quedar nada", root.listFiles()!!.isEmpty())
    }
}
