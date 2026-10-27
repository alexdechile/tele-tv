package com.tele.app.data.addon.model

/** Tipo de contenido que se puede pedir a un addon. */
enum class MediaType(val apiType: String) {
    MOVIE(MetaPreview.TYPE_MOVIE),
    SERIES(MetaPreview.TYPE_SERIES);

    companion object {
        fun all(): Set<MediaType> = entries.toSet()
        fun fromApi(apiType: String?): MediaType? = entries.firstOrNull { it.apiType == apiType }
    }
}

/** Cómo ordenar los resultados. */
enum class SortOrder {
    /** El orden que entrega el catálogo (popularidad). */
    POPULAR,

    /** Año de estreno descendente. */
    RECENT,

    /** Nota de IMDb descendente (necesita la ficha de cada título). */
    RATED
}

/** Década de producción. `to` es inclusivo. */
enum class Decade(val from: Int, val to: Int) {
    D2020(2020, 2029),
    D2010(2010, 2019),
    D2000(2000, 2009),
    D1990(1990, 1999),
    D1980(1980, 1989),
    BEFORE(1900, 1979);

    fun contains(year: Int): Boolean = year in from..to
}

/**
 * Filtros del buscador avanzado.
 *
 * Todo lo que se puede empujar al addon viaja en la URL (`search`, `genre`,
 * `skip`); lo que el protocolo no soporta se aplica en el cliente (año, nota,
 * temporadas) sobre los candidatos y su ficha.
 */
data class SearchFilters(
    val types: Set<MediaType> = MediaType.all(),
    val query: String = "",
    val genre: String? = null,
    val decade: Decade? = null,
    val minRating: Int? = null,
    val minSeasons: Int? = null,
    val sort: SortOrder = SortOrder.POPULAR
) {
    val onlySeries: Boolean get() = types.size == 1 && types.contains(MediaType.SERIES)

    /** ¿Hay algún filtro que obligue a mirar la ficha de cada título? */
    val needsDetail: Boolean
        get() = minRating != null || minSeasons != null || sort == SortOrder.RATED

    /** Sin filtros: el buscador no tiene nada que pedir todavía. */
    val isBlank: Boolean
        get() = query.isBlank() && genre == null && decade == null &&
            minRating == null && minSeasons == null
}
