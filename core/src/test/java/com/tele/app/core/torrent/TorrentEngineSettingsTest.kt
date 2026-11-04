package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los ajustes por defecto son parte del contrato de seguridad de la app: si
 * alguien los cambia, la suite lo acusa. Estos tests no tocan el nativo de
 * libtorrent (por eso corren rápido y en cualquier máquina).
 */
class TorrentEngineSettingsTest {

    @Test
    fun `por defecto no se abre el router ni se anuncia en la LAN`() {
        val settings = TorrentEngineSettings()

        assertFalse("UPnP debe venir apagado", settings.enableUpnp)
        assertFalse("NAT-PMP debe venir apagado", settings.enableNatPmp)
        assertFalse("LSD debe venir apagado", settings.enableLsd)
    }

    @Test
    fun `por defecto no se siembra y la bajada queda libre`() {
        val settings = TorrentEngineSettings()

        assertEquals(
            "la TV no reparte el material de nadie",
            64,
            settings.uploadRateLimitKbps
        )
        assertEquals(64 * 1024, settings.uploadRateLimitBytesPerSecond)
        assertTrue(
            "a 64 KB/s no satura la subida",
            settings.uploadRateLimitBytesPerSecond <= 64 * 1024
        )
        assertTrue(
            "pero tiene que alcanzar para el handshake (68 bytes) sin demoras",
            settings.uploadRateLimitBytesPerSecond > 2 * 1024
        )
        assertEquals("la bajada no se limita", 0, settings.downloadRateLimitKbps)
    }

    @Test
    fun `el tope de subida nunca llega a cero`() {
        // 0 en libtorrent = SIN tope (seguiria sembrando); y 1 B/s rompe el
        // handshake: por eso el default no puede ser ni 0 ni "casi 0".
        val settings = TorrentEngineSettings(uploadRateLimitKbps = 0)
        assertEquals(0, settings.uploadRateLimitBytesPerSecond)
        assertTrue("el default real es 64 KB/s", TorrentEngineSettings().uploadRateLimitBytesPerSecond > 1024)
    }

    @Test
    fun `si alguien quiere sembrar, el tope se respeta`() {
        val settings = TorrentEngineSettings(uploadRateLimitKbps = 200)

        assertEquals(200 * 1024, settings.uploadRateLimitBytesPerSecond)
    }

    @Test
    fun `el DHT viene encendido con privacidad para resolver metadatos`() {
        assertTrue("con DHT se resuelven metadatos sin depender de trackers UDP", TorrentEngineSettings().enableDht)
    }

    @Test
    fun `las busquedas DHT piden privacidad`() {
        val settings = TorrentEngineSettings()

        assertTrue(
            "v3.1: la busqueda DHT no debe revelar el info-hash completo (BEP-53)",
            settings.dhtPrivacyLookups
        )
        assertTrue("el DHT esta activo con privacidad", settings.enableDht)
    }

    @Test
    fun `piece_extent_affinity viene apagado para favorecer deadlines de streaming`() {
        assertFalse(
            "en false el picker concentra ancho de banda en la primera pieza sin agrupar por extents",
            TorrentEngineSettings().pieceExtentAffinity
        )
    }

    @Test
    fun `el cifrado obligatorio viene apagado para no dejar fuera a los enjambres v1`() {
        assertFalse(
            "pe_forced rechaza a los pares sin handshake cifrado y deja el video buffereando",
            TorrentEngineSettings().requireEncryption
        )
        assertTrue(
            "pero se puede pedir el perfil estricto de v3.1",
            TorrentEngineSettings(requireEncryption = true).requireEncryption
        )
    }

    @Test
    fun `listen_interfaces incluye el puerto configurado`() {
        assertEquals(
            "0.0.0.0:7000,[::]:7000",
            TorrentEngineSettings(listenPort = 7000).listenInterfaces
        )
        assertEquals(
            "0.0.0.0:7000",
            TorrentEngineSettings(listenPort = 7000, listenAllInterfaces = false).listenInterfaces
        )
    }

    @Test
    fun `sin proxy no se configura ninguno`() {
        assertEquals(null, TorrentEngineSettings().proxy)
    }
}
