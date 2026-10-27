package com.tele.app.data.addon.model

/**
 * Catálogo declarado por un addon en su manifest.
 * Un catálogo es una "fila" de contenido: películas populares, series nuevas, etc.
 *
 * Los `extra` del manifest dicen qué filtros acepta el catálogo. Se normalizan en
 * [genres] (valores de género reales), [years] (años, cuando el catálogo filtra por
 * año) y [supportsSearch] (si acepta `search=`).
 */
data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val supportsSearch: Boolean = false
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
    val description: String? = null,
    val rating: Double? = null
) {
    /** Primer año de [releaseInfo] ("2011–2019" → 2011). */
    val year: Int? get() = releaseInfo?.let(::yearOfReleaseInfo)

    val isSeries: Boolean get() = type == TYPE_SERIES

    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"
    }
}

/**
 * Un episodio concreto de una serie.
 *
 * [id] es el identificador que entienden los addons de streams: para Cinemeta
 * es el id de IMDb con sufijo de temporada/episodio (`tt0903747:1:1`), que es
 * exactamente lo que Torrentio espera para devolver las fuentes del episodio.
 */
data class Episode(
    val id: String,
    val season: Int,
    val episode: Int,
    val name: String?,
    val released: String? = null,
    val overview: String? = null
) {
    /** Etiqueta corta tipo "1x01". */
    val code: String get() = "${season}x${episode.toString().padStart(2, '0')}"
}

/** Una temporada con sus episodios ordenados. */
data class Season(
    val number: Int,
    val episodes: List<Episode>
) {
    val label: String
        get() = if (number == 0) "Especiales" else "Temporada $number"
}

/**
 * Ficha completa de un título, tal como la entrega el recurso `meta` del addon.
 *
 * Es lo que permite filtrar por valoración y temporadas, y elegir episodio:
 * un catálogo solo trae carátula, nombre y año.
 */
data class MetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val releaseInfo: String? = null,
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val runtime: String? = null,
    val status: String? = null,
    val seasons: List<Season> = emptyList()
) {
    /** Año de estreno legible ("2008–2013" → 2008). */
    val firstYear: Int? get() = year?.let(::yearOfReleaseInfo) ?: releaseInfo?.let(::yearOfReleaseInfo)

    /** Cantidad de temporadas reales (sin contar "especiales"). */
    val seasonCount: Int get() = seasons.count { it.number > 0 }

    /** Busca la temporada pedida; si no existe, devuelve la primera. */
    fun season(number: Int?): Season? =
        seasons.firstOrNull { it.number == number } ?: seasons.firstOrNull()
}

/** Extrae el primer año de cuatro dígitos de un `releaseInfo` ("2019", "2011–2019"). */
fun yearOfReleaseInfo(releaseInfo: String): Int? =
    YEAR_REGEX.find(releaseInfo)?.value?.toIntOrNull()

private val YEAR_REGEX = Regex("""(?:18|19|20)\d{2}""")

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
