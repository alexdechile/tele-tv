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
 *  - `upload_rate_limit` viene en `0` (sin tope). Subir sin límite compite con
 *    la reproducción y puede saturar el enlace de la casa. Acá hay un tope.
 *
 * Lo que **no** cambia: la sesión sigue escuchando (si no, no llegan pares
 * nuevos y el streaming se arrastra), el DHT sigue activo (sin él los magnet
 * sin tracker no resuelven) y el cifrado sigue en `pe_enabled` (acepta pares
 * cifrados y en claro, que es lo que permite conectarse a enjambres reales).
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
    /** Tope de subida en KB/s; 0 = sin tope. */
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

    /** Traduce estos ajustes al `settings_pack` que entiende libtorrent4j. */
    fun toSettingsPack(): SettingsPack = SettingsPack().apply {
        setString(settings_pack.string_types.listen_interfaces.swigValue(), listenInterfaces)

        setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), enableUpnp)
        setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), enableNatPmp)
        setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), enableLsd)

        setInteger(
            settings_pack.int_types.upload_rate_limit.swigValue(),
            uploadRateLimitKbps.coerceAtLeast(0) * BYTES_PER_KB
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
         * 800 KB/s de subida: suficiente para devolver piezas al enjambre sin
         * comerse el ancho de banda que necesita el video.
         */
        const val DEFAULT_UPLOAD_RATE_LIMIT_KBPS = 800

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
