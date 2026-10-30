package com.tele.app.data.enrich

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Lectura de las respuestas de Wikidata/Wikipedia.
 *
 * Todo es función pura sobre `JsonObject`: no hay red acá, así que se puede
 * probar con JSON de fixture y sin tocar Wikimedia. Los defaults son
 * deliberadamente tolerantes: si falta una afirmación, la ficha sale incompleta
 * pero la pantalla no se cae.
 */
internal object WikiParser {

    /** "tt0903747:1:1" → "tt0903747". Cualquier otra cosa → null. */
    fun imdbId(metaId: String?): String? =
        metaId?.substringBefore(':')?.trim()?.takeIf { IMDB.matches(it) }

    /** Del resultado de `list=search` sale el QID de la entidad. */
    fun searchEntityId(root: JsonObject?): String? =
        root?.objOrNull("query")?.arrOrNull("search")
            ?.firstOrNull { it.isJsonObject }
            ?.asJsonObject
            ?.strOrNull("title")
            ?.takeIf { QID.matches(it) }

    /** La entidad pedida dentro de la respuesta de `wbgetentities`. */
    fun entity(root: JsonObject?, entityId: String): JsonObject? =
        root?.objOrNull("entities")?.objOrNull(entityId)?.takeIf { !it.has("missing") }

    /** Afirmaciones que interesan para la ficha. */
    fun facts(entity: JsonObject, type: String): WikiFacts {
        val creators = claimItemIds(entity, PROPERTY_CREATOR)
        val directors = claimItemIds(entity, PROPERTY_DIRECTOR)

        return WikiFacts(
            // Una serie suele tener "director" por episodio (decenas); el dato
            // útil es quién la creó. Si no hay creador, se cae a director.
            crewIds = if (type == TYPE_SERIES && creators.isNotEmpty()) creators else directors,
            castIds = claimItemIds(entity, PROPERTY_CAST),
            studioIds = claimItemIds(entity, PROPERTY_PRODUCTION_COMPANY),
            countryIds = claimItemIds(entity, PROPERTY_COUNTRY),
            languageIds = claimItemIds(entity, PROPERTY_LANGUAGE),
            year = claimYear(entity, PROPERTY_PUBLICATION_DATE) ?: claimYear(entity, PROPERTY_START_TIME),
            eswikiTitle = sitelinkTitle(entity, "eswiki"),
            enwikiTitle = sitelinkTitle(entity, "enwiki")
        )
    }

    /** Etiquetas e enlaces de varias entidades, indexadas por QID. */
    fun labels(root: JsonObject?): Map<String, WikiLabel> {
        val entities = root?.objOrNull("entities") ?: return emptyMap()

        return entities.entrySet().mapNotNull { (id, value) ->
            if (!value.isJsonObject) return@mapNotNull null
            val entity = value.asJsonObject

            val labels = entity.objOrNull("labels")
            val text = labels?.objOrNull("es")?.strOrNull("value")
                ?: labels?.objOrNull("en")?.strOrNull("value")
                ?: return@mapNotNull null

            val eswiki = sitelinkTitle(entity, "eswiki")
            val enwiki = sitelinkTitle(entity, "enwiki")

            id to WikiLabel(
                text = WikiText.clean(text) ?: text,
                wikiLang = when {
                    eswiki != null -> "es"
                    enwiki != null -> "en"
                    else -> null
                },
                wikiTitle = eswiki ?: enwiki
            )
        }.toMap()
    }

    /** Texto del resumen REST; null si es desambiguación o no trae extracto. */
    fun summary(root: JsonObject?): WikiSummary? {
        root ?: return null
        if (root.strOrNull("type") == "disambiguation") return null

        val extract = WikiText.clean(root.strOrNull("extract"), SUMMARY_MAX_CHARS) ?: return null
        val page = root.objOrNull("content_urls")
            ?.objOrNull("desktop")
            ?.strOrNull("page")

        return WikiSummary(extract = extract, pageUrl = page)
    }

    // ---------------------------------------------------------------------
    // Interno
    // ---------------------------------------------------------------------

    private fun claimItemIds(entity: JsonObject, property: String): List<String> {
        val claims = entity.objOrNull("claims")?.arrOrNull(property) ?: return emptyList()

        return claims.mapNotNull { claim ->
            if (!claim.isJsonObject) return@mapNotNull null
            claim.asJsonObject
                .objOrNull("mainsnak")
                ?.objOrNull("datavalue")
                ?.objOrNull("value")
                ?.strOrNull("id")
                ?.takeIf { QID.matches(it) }
        }.distinct()
    }

    private fun claimYear(entity: JsonObject, property: String): Int? {
        val claims = entity.objOrNull("claims")?.arrOrNull(property) ?: return null

        return claims.firstNotNullOfOrNull { claim ->
            if (!claim.isJsonObject) return@firstNotNullOfOrNull null
            // "+2008-01-20T00:00:00Z" → 2008
            claim.asJsonObject
                .objOrNull("mainsnak")
                ?.objOrNull("datavalue")
                ?.objOrNull("value")
                ?.strOrNull("time")
                ?.takeIf { it.length >= 5 }
                ?.substring(1, 5)
                ?.toIntOrNull()
        }
    }

    private fun sitelinkTitle(entity: JsonObject, site: String): String? =
        entity.objOrNull("sitelinks")?.objOrNull(site)?.strOrNull("title")

    private val IMDB = Regex("^tt\\d{5,}$")
    private val QID = Regex("^Q\\d+$")

    const val TYPE_SERIES = "series"
    const val SUMMARY_MAX_CHARS = 700

    private const val PROPERTY_CREATOR = "P170"
    private const val PROPERTY_DIRECTOR = "P57"
    private const val PROPERTY_CAST = "P161"
    private const val PROPERTY_PRODUCTION_COMPANY = "P272"
    private const val PROPERTY_COUNTRY = "P495"
    private const val PROPERTY_LANGUAGE = "P364"
    private const val PROPERTY_PUBLICATION_DATE = "P577"
    private const val PROPERTY_START_TIME = "P580"
}

/** Afirmaciones crudas de una entidad (todavía sin nombres legibles). */
internal data class WikiFacts(
    val crewIds: List<String> = emptyList(),
    val castIds: List<String> = emptyList(),
    val studioIds: List<String> = emptyList(),
    val countryIds: List<String> = emptyList(),
    val languageIds: List<String> = emptyList(),
    val year: Int? = null,
    val eswikiTitle: String? = null,
    val enwikiTitle: String? = null
)

/** Nombre legible de una entidad y su página de Wikipedia, si tiene. */
internal data class WikiLabel(
    val text: String,
    val wikiLang: String?,
    val wikiTitle: String?
)

internal data class WikiSummary(
    val extract: String,
    val pageUrl: String?
)

private fun JsonObject.objOrNull(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arrOrNull(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.strOrNull(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString
