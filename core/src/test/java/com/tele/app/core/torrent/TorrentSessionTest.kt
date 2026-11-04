package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.LibTorrent
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.swig.settings_pack
import com.frostwire.jlibtorrent.swig.torrent_flags_t
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
            val infoHash = params.infoHashes.getBest()
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
            assertFalse(
                "el DHT debe quedar apagado en la sesion real",
                applied.getBoolean(settings_pack.bool_types.enable_dht.swigValue())
            )
            assertEquals(
                settings.listenInterfaces,
                applied.getString(settings_pack.string_types.listen_interfaces.swigValue())
            )
            assertEquals(
                "el tope de subida del motor tiene que ser el del default (32 KB/s)",
                TorrentEngineSettings.DEFAULT_UPLOAD_RATE_LIMIT_KBPS * 1024,
                applied.getInteger(settings_pack.int_types.upload_rate_limit.swigValue())
            )
            assertTrue(
                "las busquedas DHT deben pedir privacidad (hash truncado + sal)",
                applied.getBoolean(settings_pack.bool_types.dht_privacy_lookups.swigValue())
            )
            assertFalse(
                "piece_extent_affinity debe quedar apagado en la sesion real para streaming",
                applied.getBoolean(settings_pack.bool_types.piece_extent_affinity.swigValue())
            )
            assertEquals(
                "por defecto el cifrado acepta pares cifrados y en claro",
                settings_pack.enc_policy.pe_enabled.swigValue(),
                applied.getInteger(settings_pack.int_types.out_enc_policy.swigValue())
            )
            assertEquals(
                "la politica de entrada tiene que coincidir con la de salida",
                settings_pack.enc_policy.pe_enabled.swigValue(),
                applied.getInteger(settings_pack.int_types.in_enc_policy.swigValue())
            )
        } finally {
            session.stop()
        }
    }

    /**
     * El perfil estricto de v3.1: si alguien pide cifrado obligatorio, las dos
     * direcciones tienen que quedar en `pe_forced`. Dejarlas en el default por
     * descuido es justo el error que este test impide.
     */
    @Test
    fun `pedir cifrado obligatorio pone pe_forced en ambas direcciones`() {
        val settings = TorrentEngineSettings(listenPort = 0, requireEncryption = true)

        val session = SessionManager()
        session.start(settings.toSessionParams())
        try {
            val applied = session.settings()
            assertEquals(
                settings_pack.enc_policy.pe_forced.swigValue(),
                applied.getInteger(settings_pack.int_types.out_enc_policy.swigValue())
            )
            assertEquals(
                settings_pack.enc_policy.pe_forced.swigValue(),
                applied.getInteger(settings_pack.int_types.in_enc_policy.swigValue())
            )
        } finally {
            session.stop()
        }
    }

    /**
     * BitTorrent v2: un `btmh:` puro tiene que parsear a un info-hash SHA-256 y
     * **no** inventar un v1. El hash es el de ejemplo de la especificación
     * BEP-52, así que no depende de la red.
     */
    @Test
    fun `un magnet v2 puro expone el info-hash SHA-256`() {
        val v2Only = "magnet:?xt=urn:btmh:1220$V2_HASH&dn=v2"

        val infoHashes = AddTorrentParams.parseMagnetUri(v2Only).infoHashes

        assertTrue("deberia reconocer el info-hash v2", infoHashes.hasV2())
        assertEquals(V2_HASH, infoHashes.getV2().toString())
        assertFalse("un v2 puro no tiene info-hash v1", infoHashes.hasV1())
    }

    /** Un magnet híbrido lleva los dos y el motor elige v2 primero. */
    @Test
    fun `un magnet hibrido expone v1 y v2`() {
        val hybrid = "magnet:?xt=urn:btih:$V1_HASH&xt=urn:btmh:1220$V2_HASH&dn=hybrid"

        val infoHashes = AddTorrentParams.parseMagnetUri(hybrid).infoHashes

        assertTrue("hibrido: tiene v1", infoHashes.hasV1())
        assertTrue("hibrido: tiene v2", infoHashes.hasV2())
        assertEquals(V1_HASH, infoHashes.getV1().toString())
        assertEquals(V2_HASH, infoHashes.getV2().toString())
        // Ojo: getBest() devuelve el v1 **truncado**, no el SHA-256 (su firma es
        // Sha1Hash). Por eso el motor pregunta hasV2()/getV2() primero en vez de
        // confiar en getBest(): con un v2 puro, getBest() no es el hash que
        // identifica al torrent.
        assertTrue(
            "getBest() es v1: no sirve para identificar un torrent v2",
            infoHashes.getBest().toString().length == V1_HASH.length
        )
    }

    private companion object {
        const val V1_HASH = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"

        /** Info-hash v2 de ejemplo de BEP-52 (multihash 0x12 0x20 = SHA-256). */
        const val V2_HASH =
            "caf1e1c30e81cb361b9ee167c4aa64228a7fa4fa9f6105232b28ad099f3a302e"
    }
}
