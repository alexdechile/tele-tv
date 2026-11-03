package com.tele.app.core.torrent

import com.frostwire.jlibtorrent.SessionParams
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.swig.settings_pack

/**
 * Ajustes explícitos de la sesión libtorrent.
 *
 * Sin esto el motor arranca con los valores por defecto de libtorrent 1.2, que
 * no son los que conviene a una app de TV que se usa en la casa de uno:
 *
 *  - `enable_upnp` y `enable_natpmp` vienen en `true`: el motor le pide al
 *    router que abra un puerto hacia el TV box. El usuario nunca lo autoriza y
 *    el agujero queda abierto mientras la app corre. Acá se apagan.
 *  - `enable_lsd` viene en `true`: anuncia el torrent por multicast en la red
 *    local, así que otros equipos del mismo WiFi se enteran de lo que se está
 *    bajando. Acá se apaga.
 *  - `listen_interfaces` viene fijo en `0.0.0.0:6881` pero nadie lo declara:
 *    si algo lo cambia, nadie lo revisa. Acá queda explícito y configurable.
 *  - `upload_rate_limit` viene en `0`, que en libtorrent significa **sin
 *    tope**: el motor repartía el archivo a todo lo que diera la línea. Acá
 *    viene en 32 KB/s: no alcanza para repartir una pieza, pero deja pasar el
 *    protocolo (ver [DEFAULT_UPLOAD_RATE_LIMIT_KBPS]).
 *  - `enable_dht` viene en `true` y publica la consulta en la red DHT. Acá se
 *    apaga: se resuelve por los trackers del magnet y se anuncia menos gente.
 *
 * Lo que **no** cambia: la sesión sigue escuchando (si no, no llegan pares
 * nuevos y el streaming se arrastra) y el cifrado sigue en `pe_enabled`
 * (acepta pares cifrados y en claro, que es lo que permite conectarse a
 * enjambres reales). [requireEncryption] permite forzarlo si algún día se
 * quiere un perfil estricto de v3.1.
 *
 * [proxy] existe para que mañana se pueda mandar todo el tráfico por un SOCKS5
 * del homelab; hoy viene en `null` y no cambia nada.
 */
data class TorrentEngineSettings(
    /** Puerto de escucha. 0 deja que el sistema elija uno al azar. */
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    /** Escuchar en IPv4 e IPv6 (`0.0.0.0:p,[::]:p`) o solo IPv4. */
    val listenAllInterfaces: Boolean = true,
    val enableUpnp: Boolean = false,
    val enableNatPmp: Boolean = false,
    val enableLsd: Boolean = false,
    /**
     * DHT apagado: sin él no se busca ni se anuncia nada fuera de los trackers
     * del magnet. Menos superficie y menos gente enterada de lo que se abre.
     * Los magnet igual resuelven por los trackers que arma `TorrentLinks`.
     */
    val enableDht: Boolean = false,
    /**
     * Búsquedas DHT con privacidad (`dht_privacy_lookups`, libtorrent 2.0).
     *
     * Con esto puesto, el nodo pide a la red DHT usando un hash **truncado** más
     * una sal por consulta, en vez del info-hash completo: quien espíe el
     * tráfico no aprende qué torrent se está buscando. Es la mitigación que
     * pide BitTorrent v3.1 para DHT (BEP-53) y viene **encendida**, aunque hoy
     * sea inocua porque [enableDht] está apagado: queda lista para el día que
     * alguien encienda el DHT.
     */
    val dhtPrivacyLookups: Boolean = true,
    /**
     * Exigir cifrado del protocolo de pares en **ambas** direcciones
     * (`out_enc_policy`/`in_enc_policy` = `pe_forced`).
     *
     * v3.1 (BEP-53) lo vuelve obligatorio. Viene **apagado a propósito**: en
     * enjambres públicos de torrents v1 todavía hay clientes viejos que no
     * negocian el handshake cifrado, y forzarlo los echa y deja el video
     * buffereando. Con `pe_enabled` (el valor de libtorrent) se aceptan pares
     * cifrados y en claro, que es lo que hace que el streaming funcione hoy.
     *
     * El cifrado que implementa libtorrent es MSE/PE (RC4), no TLS/MuTP: eso
     * ofusca el contenido frente a quien mira el cable, pero no es
     * criptografía moderna. Ver la nota en [TorrentEngine].
     */
    val requireEncryption: Boolean = false,
    /**
     * Tope de subida en KB/s. El valor por defecto es tan bajo que la app no
     * reparte material útil (una pieza de 1 MB tardaría más de dos minutos en
     * salir, y el video dura minutos), pero deja pasar el protocolo.
     *
     * **Por qué no es 0 ni 1 B/s:** `upload_rate_limit = 0` en libtorrent
     * significa *sin tope* — lo contrario. Y 1 B/s **rompe la conexión**: el
     * limitador cuenta también los mensajes de control, así que el handshake
     * (68 bytes) tarda un minuto y los metadatos del magnet nunca llegan. Se
     * midió en el KM6 (2026-10-31): con 1 B/s la app se quedaba en "Obteniendo
     * metadatos del torrent…".
     */
    val uploadRateLimitKbps: Int = DEFAULT_UPLOAD_RATE_LIMIT_KBPS,
    /** Tope de bajada en KB/s; 0 = sin tope (lo normal para streaming). */
    val downloadRateLimitKbps: Int = DEFAULT_DOWNLOAD_RATE_LIMIT_KBPS,
    val proxy: ProxyConfig? = null
) {

    /** Lista de interfaces para libtorrent: `"0.0.0.0:6881,[::]:6881"`. */
    val listenInterfaces: String
        get() = if (listenAllInterfaces) {
            "0.0.0.0:$listenPort,[::]:$listenPort"
        } else {
            "0.0.0.0:$listenPort"
        }

    /** Bytes por segundo que se permiten subir. */
    val uploadRateLimitBytesPerSecond: Int
        get() = uploadRateLimitKbps.coerceAtLeast(0) * BYTES_PER_KB

    /** Traduce estos ajustes al `settings_pack` que entiende jlibtorrent. */
    fun toSettingsPack(): SettingsPack = SettingsPack().apply {
        setString(settings_pack.string_types.listen_interfaces.swigValue(), listenInterfaces)

        setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), enableUpnp)
        setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), enableNatPmp)
        setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), enableLsd)
        setBoolean(settings_pack.bool_types.enable_dht.swigValue(), enableDht)
        setBoolean(
            settings_pack.bool_types.dht_privacy_lookups.swigValue(),
            dhtPrivacyLookups
        )

        // Política de cifrado. `pe_enabled` es el valor de libtorrent (acepta
        // ambos); `pe_forced` rechaza a todo par que no cifre. Se declaran las
        // dos direcciones porque libtorrent las configura por separado y dejar
        // una en el default hace que la intención no se cumpla.
        val encryptionPolicy = if (requireEncryption) {
            settings_pack.enc_policy.pe_forced
        } else {
            settings_pack.enc_policy.pe_enabled
        }
        setInteger(
            settings_pack.int_types.out_enc_policy.swigValue(),
            encryptionPolicy.swigValue()
        )
        setInteger(
            settings_pack.int_types.in_enc_policy.swigValue(),
            encryptionPolicy.swigValue()
        )

        setInteger(
            settings_pack.int_types.upload_rate_limit.swigValue(),
            uploadRateLimitBytesPerSecond
        )
        setInteger(
            settings_pack.int_types.download_rate_limit.swigValue(),
            downloadRateLimitKbps.coerceAtLeast(0) * BYTES_PER_KB
        )

        proxy?.let { proxyConfig ->
            val type = when (proxyConfig.type) {
                ProxyType.SOCKS5 -> settings_pack.proxy_type_t.socks5.swigValue()
                ProxyType.HTTP -> settings_pack.proxy_type_t.http.swigValue()
            }
            setInteger(settings_pack.int_types.proxy_type.swigValue(), type)
            setInteger(settings_pack.int_types.proxy_port.swigValue(), proxyConfig.port)
            setString(settings_pack.string_types.proxy_hostname.swigValue(), proxyConfig.host)
        }
    }

    /** Parámetros de arranque que aplican estos ajustes a la sesión. */
    fun toSessionParams(): SessionParams = SessionParams(toSettingsPack())

    companion object {
        const val DEFAULT_LISTEN_PORT = 6881

        /**
         * 32 KB/s: la app no siembra de forma útil (32 KB/s ≈ 115 MB por hora
         * de reproducción, y solo mientras el video está abierto), pero mantiene
         * la reciprocidad mínima que el protocolo espera.
         *
         * Historial de este número, medido en el KM6 el 2026-10-31:
         *  - 800 KB/s (valor original): repartía de verdad.
         *  - 0: libtorrent lo interpreta como **sin tope** (seguía sembrando).
         *  - 1 B/s: rompe el handshake; la app quedaba en "Obteniendo metadatos".
         *  - 4 KB/s: los metadatos llegan, pero los peers nos chokean y la
         *    bajada cae a ~70 KB/s (no alcanza para streaming 1080p).
         */
        const val DEFAULT_UPLOAD_RATE_LIMIT_KBPS = 32

        /** 0 = sin tope; bajar en la TV se quiere lo más rápido posible. */
        const val DEFAULT_DOWNLOAD_RATE_LIMIT_KBPS = 0

        private const val BYTES_PER_KB = 1024
    }
}

/** Proxy opcional para el tráfico entre pares. */
data class ProxyConfig(
    val host: String,
    val port: Int,
    val type: ProxyType = ProxyType.SOCKS5
)

enum class ProxyType { SOCKS5, HTTP }
