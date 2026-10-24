package com.tele.app.data.addon

/**
 * Addons que la app consulta por defecto.
 *
 * Ambos son servicios públicos del ecosistema Stremio:
 *  - Cinemeta: catálogos y metadatos (usa IDs de IMDb, `tt...`).
 *  - Torrentio: convierte ese ID en fuentes torrent.
 *
 * Nota: Torrentio es un servicio de terceros que puede cambiar de dominio o
 * dejar de responder; si eso pasa, basta con editar [URLS] (o, más adelante,
 * leer la lista desde ajustes/DB en lugar de tenerla fija aquí).
 */
object DefaultAddons {

    const val CINEMETA = "https://v3-cinemeta.strem.io"
    const val TORRENTIO = "https://torrentio.strem.fun"

    val URLS: List<String> = listOf(CINEMETA, TORRENTIO)
}
