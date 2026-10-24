package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.SessionManager
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Prueba el motor contra el binario **nativo real** de libtorrent (JVM Linux,
 * no la .so de Android). No depende de la red ni de semillas: verifica lo que
 * rompería la app en la TV silenciosamente, es decir que el nativo cargue y que
 * las llamadas exactas que usa [TorrentEngine] sean aceptadas.
 */
class TorrentSessionTest {

    private val magnet = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c" +
        "&dn=Big+Buck+Bunny" +
        "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"

    @Test
    fun `el nativo de libtorrent carga`() {
        val version = LibTorrent.version()
        assertNotNull(version)
        assertTrue("version inesperada: $version", version.isNotBlank())
    }

    @Test
    fun `la sesion arranca y acepta un magnet`() {
        val saveDir = File(System.getProperty("java.io.tmpdir"), "tele-tv-test").apply { mkdirs() }

        val session = SessionManager()
        session.start()
        try {
            assertTrue("la sesion deberia estar corriendo", session.isRunning())

            val params = AddTorrentParams.parseMagnetUri(magnet)
            val infoHash = params.infoHashes.best
            assertNotNull("el magnet deberia exponer un info hash", infoHash)
            assertEquals(
                "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
                infoHash.toString()
            )

            // Valida contra el nativo la firma y los flags que usa TorrentEngine.
            session.download(magnet, saveDir, torrent_flags_t())
        } finally {
            session.stop()
        }
    }
}
