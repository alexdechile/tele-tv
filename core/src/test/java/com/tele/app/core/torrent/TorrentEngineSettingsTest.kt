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
    fun `hay tope de subida y la bajada queda libre`() {
        val settings = TorrentEngineSettings()

        assertTrue(
            "la subida necesita un tope para no comerse el ancho de banda del video",
            settings.uploadRateLimitKbps > 0
        )
        assertEquals("la bajada no se limita", 0, settings.downloadRateLimitKbps)
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
