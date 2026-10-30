package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.SessionManager
import org.libtorrent4j.swig.settings_pack
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

    /**
     * Lo que de verdad importa del endurecimiento: que libtorrent **acepte** el
     * settings_pack y que la sesión quede con UPnP/NAT-PMP/LSD apagados. Si el
     * nativo ignorara alguna de las claves, este test lo delata.
     */
    @Test
    fun `la sesion arranca con los ajustes endurecidos`() {
        val settings = TorrentEngineSettings(listenPort = 0)

        val session = SessionManager()
        session.start(settings.toSessionParams())
        try {
            assertTrue("la sesion deberia estar corriendo", session.isRunning())

            val applied = session.settings()
            assertFalse(
                "UPnP debe quedar apagado en la sesion real",
                applied.getBoolean(settings_pack.bool_types.enable_upnp.swigValue())
            )
            assertFalse(
                "NAT-PMP debe quedar apagado en la sesion real",
                applied.getBoolean(settings_pack.bool_types.enable_natpmp.swigValue())
            )
            assertFalse(
                "LSD debe quedar apagado en la sesion real",
                applied.getBoolean(settings_pack.bool_types.enable_lsd.swigValue())
            )
            assertEquals(
                settings.listenInterfaces,
                applied.getString(settings_pack.string_types.listen_interfaces.swigValue())
            )
            assertEquals(
                settings.uploadRateLimitKbps * 1024,
                applied.getInteger(settings_pack.int_types.upload_rate_limit.swigValue())
            )
        } finally {
            session.stop()
        }
    }
}
