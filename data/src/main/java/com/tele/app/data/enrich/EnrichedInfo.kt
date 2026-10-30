package com.tele.app.data.enrich

/**
 * Ficha cultural de un título, sacada de Wikidata y Wikipedia.
 *
 * Todo lo de acá es **texto plano ya limpiado**: nunca HTML ni JSON crudo.
 * Eso es deliberado: la UI solo dibuja `Text`, así que una respuesta rara de
 * Wikipedia no puede inyectar nada en la pantalla.
 *
 * Los hechos (año, productora, reparto) vienen de Wikidata, que es **CC0**; las
 * biografías y sinopsis vienen de Wikipedia, que es **CC BY-SA** y por eso se
 * muestra la atribución ([license] y [sourceUrl]).
 */
data class EnrichedInfo(
    /** "Dirección" para películas, "Creada por" para series. */
    val crewLabel: String,
    /** Quiénes dirigieron o crearon, en el orden que da Wikidata. */
    val crew: List<String> = emptyList(),
    /** Primer párrafo de la biografía de [crew] (español si existe). */
    val biography: String? = null,
    val cast: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val year: Int? = null,
    /** Sinopsis en español; complementa la descripción en inglés de Cinemeta. */
    val overview: String? = null,
    val sourceUrl: String? = null,
    val license: String = "Wikipedia (CC BY-SA)"
) {

    val hasContent: Boolean
        get() = crew.isNotEmpty() || cast.isNotEmpty() || studios.isNotEmpty() || overview != null

    /** "Productora: Sony · Estados Unidos · inglés · 2008" (solo lo disponible). */
    fun creditLine(): String = buildList {
        studios.firstOrNull()?.let { add("Productora: $it") }
        countries.firstOrNull()?.let { add(it) }
        languages.firstOrNull()?.let { add(it) }
        year?.let { add(it.toString()) }
    }.joinToString(" · ")

    /** "Vince Gilligan, Adam Bernstein" → para pintar en una sola línea. */
    fun crewLine(): String = crew.joinToString(", ")

    fun castLine(): String = cast.joinToString(", ")
}
