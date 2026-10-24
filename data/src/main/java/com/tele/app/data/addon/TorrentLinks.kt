package com.tele.app.data.addon

/** Construcción de magnet URIs a partir de un infoHash. */
object TorrentLinks {

    /** Trackers públicos usados para que el enjambre arranque sin depender del addon. */
    private val TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce"
    )

    /**
     * Devuelve `magnet:?xt=urn:btih:<hash>&dn=<nombre>&tr=<trackers>`.
     * El nombre es opcional y solo se usa para que el motor muestre algo legible.
     */
    fun magnet(infoHash: String, displayName: String? = null): String {
        val builder = StringBuilder("magnet:?xt=urn:btih:").append(infoHash)
        displayName
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.append("&dn=").append(encode(it)) }
        TRACKERS.forEach { builder.append("&tr=").append(encode(it)) }
        return builder.toString()
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
