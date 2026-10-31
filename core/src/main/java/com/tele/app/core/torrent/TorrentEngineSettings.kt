package com.tele.app.core.torrent

import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.swig.settings_pack

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
 *    viene en 0 KB/s, o sea que la app **no sube** (se traduce a 1 B/s, ver
 *    [NO_UPLOAD_BYTES_PER_SECOND]).
 *  - `enable_dht` viene en `true` y publica la consulta en la red DHT. Acá se
 *    apaga: se resuelve por los trackers del magnet y se anuncia menos gente.
 *
 * Lo que **no** cambia: la sesión sigue escuchando (si no, no llegan pares
 * nuevos y el streaming se arrastra) y el cifrado sigue en `pe_enabled`
 * (acepta pares cifrados y en claro, que es lo que permite conectarse a
 * enjambres reales).
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
     * Tope de subida en KB/s. **0 = no subir**: la app no reparte el material
     * de nadie. Ojo con la trampa: libtorrent interpreta `upload_rate_limit`
     * en 0 como "sin tope", así que 0 se traduce a [NO_UPLOAD_BYTES_PER_SECOND]
     * (1 B/s), no se manda el 0 tal cual.
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

    /**
     * Bytes por segundo que se permiten subir.
     *
     * `uploadRateLimitKbps = 0` significa "no subir", y en bytes por segundo se
     * expresa con 1 (un byte por segundo): un valor bajo pero distinto de cero,
     * porque el 0 de libtorrent es "ilimitado".
     */
    val uploadRateLimitBytesPerSecond: Int
        get() = if (uploadRateLimitKbps > 0) {
            uploadRateLimitKbps * BYTES_PER_KB
        } else {
            NO_UPLOAD_BYTES_PER_SECOND
        }

    /** Traduce estos ajustes al `settings_pack` que entiende libtorrent4j. */
    fun toSettingsPack(): SettingsPack = SettingsPack().apply {
        setString(settings_pack.string_types.listen_interfaces.swigValue(), listenInterfaces)

        setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), enableUpnp)
        setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), enableNatPmp)
        setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), enableLsd)
        setBoolean(settings_pack.bool_types.enable_dht.swigValue(), enableDht)

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
         * 0 = no subir. Decisión de producto: la TV no reparte el material de
         * nadie, así que esta app no siembra. Si alguien necesita ratio (tracker
         * privado), cambia este valor a 200/800.
         */
        const val DEFAULT_UPLOAD_RATE_LIMIT_KBPS = 0

        /**
         * El "cero" que hay que mandarle a libtorrent para **no** subir.
         *
         * `upload_rate_limit = 0` en libtorrent significa *sin tope*, justo lo
         * contrario de lo que parece: con 0 el motor reparte a todo lo que dé la
         * línea. Con 1 byte por segundo el envío de datos queda efectivamente
         * cortado (los mensajes de control del protocolo son de decenas de
         * bytes y no dependen de este tope).
         */
        const val NO_UPLOAD_BYTES_PER_SECOND = 1

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
