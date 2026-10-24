package com.tele.app.data.addon.model

/**
 * Catálogo declarado por un addon en su manifest.
 * Un catálogo es una "fila" de contenido: películas populares, series nuevas, etc.
 */
data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String
)

/**
 * Manifest Stremio v3 ya normalizado.
 *
 * En el protocolo original `resources` puede ser una lista de strings
 * (`["catalog", "meta"]`) o una lista de objetos (`{name, types, idPrefixes}`).
 * Aquí se reduce siempre a nombres de recurso.
 */
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val types: List<String>,
    val catalogs: List<AddonCatalog>,
    val resources: List<String>
) {
    /** Indica si el addon ofrece el recurso indicado ("catalog", "meta", "stream"). */
    fun provides(resource: String): Boolean = resources.contains(resource)
}

/** Ítem de catálogo (meta preview): lo que se pinta como carátula en la UI. */
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val releaseInfo: String? = null,
    val description: String? = null
)

/** Fuente reproducible devuelta por un addon de streams (Torrentio y similares). */
data class StreamSource(
    val addon: String,
    val label: String,
    val detail: String?,
    val infoHash: String? = null,
    val url: String? = null,
    val fileIndex: Int? = null,
    val fileName: String? = null,
    val seeders: Int? = null
) {
    val isPlayable: Boolean
        get() = !infoHash.isNullOrBlank() || !url.isNullOrBlank()
}

/** Una fila del home: título visible + sus carátulas. */
data class HomeRow(
    val id: String,
    val title: String,
    val items: List<MetaPreview>
)
