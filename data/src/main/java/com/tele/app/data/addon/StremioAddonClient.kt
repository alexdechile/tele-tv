package com.tele.app.data.addon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tele.app.data.addon.model.AddonCatalog
import com.tele.app.data.addon.model.AddonManifest
import com.tele.app.data.addon.model.Episode
import com.tele.app.data.addon.model.MetaDetail
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.Season
import com.tele.app.data.addon.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error de protocolo o de red hablando con un addon. */
class AddonException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Cliente del protocolo de addons de Stremio (v3).
 *
 * Un addon es simplemente un servidor HTTP que expone:
 *  - GET /manifest.json                     → qué ofrece
 *  - GET /catalog/{type}/{id}/{extra}.json  → {"metas":[...]}
 *  - GET /meta/{type}/{id}.json             → {"meta":{...}}
 *  - GET /stream/{type}/{id}.json           → {"streams":[...]}
 *
 * Se usa OkHttp + Gson en modo lectura manual del JSON: el manifest real cambia
 * de forma (`resources` puede ser lista de strings o de objetos) y conviene no
 * atarse a DTOs rígidos.
 *
 * Las URLs se arman en [StremioUrls]: el segmento de `extra` no se puede escapar
 * o el addon ignora los filtros sin avisar.
 */
class StremioAddonClient(
    private val httpClient: OkHttpClient = createHttpClient()
) {

    /** Descarga y normaliza el manifest de un addon. */
    suspend fun manifest(addonUrl: String): AddonManifest {
        val root = fetchJson(StremioUrls.manifest(addonUrl))

        return AddonManifest(
            id = root.stringOrNull("id") ?: addonUrl,
            name = root.stringOrNull("name") ?: addonUrl,
            version = root.stringOrNull("version") ?: "0",
            description = root.stringOrNull("description"),
            types = root.stringList("types"),
            catalogs = parseCatalogs(root.arrayOrNull("catalogs")),
            resources = parseResources(root.arrayOrNull("resources"))
        )
    }

    /**
     * Lista los ítems de un catálogo concreto.
     *
     * [extras] son los filtros del protocolo (`search`, `genre`, `skip`). El
     * llamador decide cuáles manda según lo que el manifest declare.
     */
    suspend fun catalog(
        addonUrl: String,
        type: String,
        catalogId: String,
        extras: Map<String, String> = emptyMap()
    ): List<MetaPreview> {
        val root = fetchJson(StremioUrls.catalog(addonUrl, type, catalogId, extras))
        return root.arrayOrNull("metas").elements()
            .mapNotNull { it.asObjectOrNull()?.toMetaPreview() }
    }

    /** Ficha completa de un título: nota, géneros, runtime y temporadas/episodios. */
    suspend fun meta(addonUrl: String, type: String, id: String): MetaDetail? {
        val root = fetchJson(StremioUrls.meta(addonUrl, type, id))
        return root.get("meta")?.asObjectOrNull()?.toMetaDetail()
    }

    /** Lista las fuentes disponibles para un ítem (película, serie o episodio). */
    suspend fun streams(addonUrl: String, addonName: String, type: String, id: String): List<StreamSource> {
        val root = fetchJson(StremioUrls.stream(addonUrl, type, id))
        return root.arrayOrNull("streams").elements()
            .mapNotNull { it.asObjectOrNull()?.toStreamSource(addonName) }
    }

    // ---------------------------------------------------------------------
    // Parseo
    // ---------------------------------------------------------------------

    private fun parseCatalogs(array: JsonArray?): List<AddonCatalog> =
        array.elements().mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val type = obj.stringOrNull("type") ?: return@mapNotNull null
            val id = obj.stringOrNull("id") ?: return@mapNotNull null

            val extras = obj.arrayOrNull("extra").elements().mapNotNull { it.asObjectOrNull() }
            val genreOptions = extras
                .firstOrNull { it.stringOrNull("name") == "genre" }
                ?.arrayOrNull("options")
                .elements()
                .mapNotNull { option -> option.takeIf { it.isJsonPrimitive }?.asString }

            AddonCatalog(
                type = type,
                id = id,
                name = obj.stringOrNull("name") ?: id,
                // El catálogo `year` usa el "género" para los años; se separan.
                genres = genreOptions.filterNot { it.matches(YEAR_ONLY) },
                years = genreOptions.mapNotNull { it.toIntOrNull() },
                supportsSearch = extras.any { it.stringOrNull("name") == "search" }
            )
        }

    /**
     * `resources` puede venir como `["catalog", "meta"]` o como
     * `[{"name": "stream", "types": [...]}]`. Se normaliza a nombres.
     */
    private fun parseResources(array: JsonArray?): List<String> =
        array.elements().mapNotNull { element ->
            when {
                element.isJsonPrimitive -> element.asString
                element.isJsonObject -> element.asJsonObject.stringOrNull("name")
                else -> null
            }
        }

    private fun JsonObject.toMetaPreview(): MetaPreview? {
        val id = stringOrNull("id") ?: stringOrNull("imdb_id") ?: return null
        val name = stringOrNull("name") ?: return null
        return MetaPreview(
            id = id,
            type = stringOrNull("type") ?: MetaPreview.TYPE_MOVIE,
            name = name,
            poster = stringOrNull("poster"),
            background = stringOrNull("background"),
            releaseInfo = stringOrNull("releaseInfo") ?: stringOrNull("year"),
            description = stringOrNull("description"),
            rating = doubleOrNull("imdbRating")
        )
    }

    private fun JsonObject.toMetaDetail(): MetaDetail? {
        val id = stringOrNull("id") ?: return null
        val name = stringOrNull("name") ?: return null

        return MetaDetail(
            id = id,
            type = stringOrNull("type") ?: MetaPreview.TYPE_MOVIE,
            name = name,
            poster = stringOrNull("poster"),
            background = stringOrNull("background"),
            logo = stringOrNull("logo"),
            description = stringOrNull("description"),
            year = stringOrNull("year"),
            releaseInfo = stringOrNull("releaseInfo"),
            genres = (arrayOrNull("genres") ?: arrayOrNull("genre")).stringList(),
            rating = doubleOrNull("imdbRating"),
            runtime = stringOrNull("runtime"),
            status = stringOrNull("status"),
            seasons = parseSeasons(arrayOrNull("videos"))
        )
    }

    /** Agrupa los `videos` del meta en temporadas con sus episodios ordenados. */
    private fun parseSeasons(videos: JsonArray?): List<Season> =
        videos.elements()
            .mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                val season = obj.intOrNull("season") ?: return@mapNotNull null
                val episode = obj.intOrNull("episode") ?: return@mapNotNull null
                val id = obj.stringOrNull("id") ?: return@mapNotNull null

                Episode(
                    id = id,
                    season = season,
                    episode = episode,
                    name = obj.stringOrNull("name"),
                    released = obj.stringOrNull("released"),
                    overview = obj.stringOrNull("overview")
                )
            }
            .groupBy { it.season }
            .map { (number, episodes) -> Season(number, episodes.sortedBy { it.episode }) }
            .sortedBy { it.number }

    private fun JsonObject.toStreamSource(addonName: String): StreamSource? {
        val infoHash = stringOrNull("infoHash")
        val directUrl = stringOrNull("url")
        if (infoHash.isNullOrBlank() && directUrl.isNullOrBlank()) return null

        val title = stringOrNull("title")
        val fileName = get("behaviorHints")
            ?.asObjectOrNull()
            ?.stringOrNull("filename")

        return StreamSource(
            addon = addonName,
            label = stringOrNull("name")?.replace("\n", " · ") ?: addonName,
            detail = title,
            infoHash = infoHash,
            url = directUrl,
            fileIndex = intOrNull("fileIdx"),
            fileName = fileName,
            seeders = SEEDERS_REGEX.find(title.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        )
    }

    // ---------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------

    private suspend fun fetchJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AddonException("HTTP ${response.code} en ${response.request.url}")
            }
            val body = response.body?.string()
                ?: throw AddonException("Respuesta vacía de ${response.request.url}")

            try {
                JsonParser.parseString(body).asJsonObject
            } catch (error: Exception) {
                throw AddonException("JSON inválido de ${response.request.url}", error)
            }
        }
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun com.google.gson.JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonArray?.elements(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()

    private fun JsonArray?.stringList(): List<String> =
        elements().mapNotNull { it.takeIf { element -> element.isJsonPrimitive }?.asString }

    private fun JsonObject.stringList(name: String): List<String> =
        arrayOrNull(name).stringList()

    companion object {
        const val DEFAULT_LIMIT = 24
        private val SEEDERS_REGEX = Regex("""👤\s*(\d+)""")
        private val YEAR_ONLY = Regex("""\d{4}""")

        private fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
