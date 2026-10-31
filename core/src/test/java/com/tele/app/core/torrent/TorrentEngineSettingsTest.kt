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
            32,
            settings.uploadRateLimitKbps
        )
        assertEquals(32 * 1024, settings.uploadRateLimitBytesPerSecond)
        assertTrue(
            "a 32 KB/s una pieza de 1 MB tarda mas de 30 s: el enjambre no la puede pedir",
            settings.uploadRateLimitBytesPerSecond <= 32 * 1024
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
        assertTrue("el default real es 32 KB/s", TorrentEngineSettings().uploadRateLimitBytesPerSecond > 1024)
    }

    @Test
    fun `si alguien quiere sembrar, el tope se respeta`() {
        val settings = TorrentEngineSettings(uploadRateLimitKbps = 200)

        assertEquals(200 * 1024, settings.uploadRateLimitBytesPerSecond)
    }

    @Test
    fun `el DHT viene apagado`() {
        assertFalse("sin DHT se anuncia a menos gente", TorrentEngineSettings().enableDht)
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
