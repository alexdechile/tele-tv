package com.tele.app.data.enrich

import kotlinx.coroutines.CancellationException

/**
 * Arma la ficha cultural de un título a partir de Wikidata + Wikipedia.
 *
 * Se eligió a propósito **no scrapear HTML**: se usan las APIs oficiales
 * (`wbgetentities`, `list=search`, `rest_v1/page/summary`) que devuelven JSON
 * y texto plano. Eso evita parsear HTML ajeno (donde vive la mayoría de los
 * bugs y de las sorpresas), no necesita WebView ni JavaScript, y respeta la
 * política de Wikimedia.
 *
 * Flujo (máximo 5 requests, después queda en caché):
 *  1. IMDb id (`tt…`) → entidad de Wikidata por la propiedad P345.
 *  2. Afirmaciones: director/creador, reparto, productora, país, idioma, año.
 *  3. Una sola llamada para las etiquetas de todas esas entidades.
 *  4. Biografía del director (resumen de su artículo, español).
 *  5. Sinopsis en español del título.
 *
 * Si cualquier paso falla, se devuelve null y la pantalla sigue con los datos
 * de Cinemeta: el enriquecimiento es opcional por diseño.
 */
class WikipediaEnricher(
    private val client: WikiClient = WikiClient()
) {

    private val cache = object : LinkedHashMap<String, EnrichedInfo>(CACHE_INITIAL, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EnrichedInfo>?) =
            size > MAX_CACHED
    }

    /**
     * [metaId] es el id de Stremio/Cinemeta (`tt0903747` o `tt0903747:1:1`);
     * [type] es "movie" o "series". Devuelve null si el título no es de IMDb o
     * si Wikipedia no tiene nada útil.
     */
    suspend fun enrich(metaId: String?, type: String, fallbackYear: Int? = null): EnrichedInfo? {
        val imdbId = WikiParser.imdbId(metaId) ?: return null

        synchronized(cache) { cache[imdbId] }?.let { return it }

        val info = try {
            load(imdbId, type, fallbackYear)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        } ?: return null

        synchronized(cache) { cache[imdbId] = info }
        return info
    }

    /** Cuántas fichas se guardan en memoria (una por título abierto). */
    fun cachedCount(): Int = synchronized(cache) { cache.size }

    private suspend fun load(imdbId: String, type: String, fallbackYear: Int?): EnrichedInfo? {
        val entityId = WikiParser.searchEntityId(
            client.getJson(WikiUrls.imdbSearch(imdbId))
        ) ?: return null

        val entity = WikiParser.entity(
            client.getJson(WikiUrls.entity(entityId)),
            entityId
        ) ?: return null

        val facts = WikiParser.facts(entity, type)

        val ids = (
            facts.crewIds.take(MAX_CREW) +
                facts.castIds.take(MAX_CAST) +
                facts.studioIds.take(MAX_STUDIOS) +
                facts.countryIds.take(MAX_PLACES) +
                facts.languageIds.take(MAX_PLACES)
            ).distinct()

        if (ids.isEmpty()) return null

        val labels = client.getJson(WikiUrls.labels(ids))
            ?.let(WikiParser::labels)
            ?: return null

        val crew = facts.crewIds.take(MAX_CREW).mapNotNull { labels[it]?.text }
        val cast = facts.castIds.mapNotNull { labels[it]?.text }.take(MAX_CAST)
        val studios = facts.studioIds.mapNotNull { labels[it]?.text }.take(MAX_STUDIOS)
        val countries = facts.countryIds.mapNotNull { labels[it]?.text }.take(MAX_PLACES)
        val languages = facts.languageIds.mapNotNull { labels[it]?.text }.take(MAX_PLACES)

        val biography = biographyOf(labels, facts.crewIds.firstOrNull())
        val overview = overviewOf(facts)

        if (crew.isEmpty() && cast.isEmpty() && studios.isEmpty() && overview == null) return null

        return EnrichedInfo(
            crewLabel = if (type == WikiParser.TYPE_SERIES) "Creada por" else "Dirección",
            crew = crew,
            biography = biography,
            cast = cast,
            studios = studios,
            countries = countries,
            languages = languages,
            year = facts.year ?: fallbackYear,
            overview = overview?.first,
            sourceUrl = overview?.second
        )
    }

    /** Primer párrafo del artículo del director/creador (en español si hay). */
    private suspend fun biographyOf(labels: Map<String, WikiLabel>, crewId: String?): String? {
        val label = crewId?.let { labels[it] } ?: return null
        val title = label.wikiTitle ?: return null
        val lang = label.wikiLang ?: return null

        val summary = client.getJson(WikiUrls.summary(lang, title))
            ?.let(WikiParser::summary)
            ?: return null

        return WikiText.clean(summary.extract, BIO_MAX_CHARS)
    }

    /**
     * Sinopsis desde Wikipedia **solo en español**: si el título no tiene
     * artículo en español, Cinemeta ya muestra una descripción en inglés y no
     * vale la pena duplicarla.
     */
    private suspend fun overviewOf(facts: WikiFacts): Pair<String, String?>? {
        val title = facts.eswikiTitle ?: return null

        val summary = client.getJson(WikiUrls.summary("es", title))
            ?.let(WikiParser::summary)
            ?: return null

        return summary.extract to (summary.pageUrl ?: WikiUrls.pageUrl("es", title))
    }

    private companion object {
        const val CACHE_INITIAL = 32
        const val MAX_CACHED = 48

        const val MAX_CREW = 2
        const val MAX_CAST = 6
        const val MAX_STUDIOS = 2
        const val MAX_PLACES = 2

        /** La biografía en pantalla es un párrafo corto, no el artículo. */
        const val BIO_MAX_CHARS = 420
    }
}
