package com.tele.app.data.addon

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tele.app.data.addon.model.AddonCatalog
import com.tele.app.data.addon.model.AddonManifest
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 *  - GET /catalog/{type}/{id}.json          → {"metas":[...]}
 *  - GET /stream/{type}/{id}.json           → {"streams":[...]}
 *
 * Se usa OkHttp + Gson en modo lectura manual del JSON: el manifest real cambia
 * de forma (`resources` puede ser lista de strings o de objetos) y conviene no
 * atarse a DTOs rígidos.
 */
class StremioAddonClient(
    private val httpClient: OkHttpClient = createHttpClient()
) {

    /** Descarga y normaliza el manifest de un addon. */
    suspend fun manifest(addonUrl: String): AddonManifest {
        val url = baseUrl(addonUrl).newBuilder()
            .addPathSegment("manifest.json")
            .build()
        val root = fetchJson(url)

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

    /** Lista los ítems de un catálogo concreto. */
    suspend fun catalog(
        addonUrl: String,
        catalog: AddonCatalog,
        skip: Int = 0,
        limit: Int = DEFAULT_LIMIT
    ): List<MetaPreview> {
        val builder = baseUrl(addonUrl).newBuilder()
            .addPathSegment("catalog")
            .addPathSegment(catalog.type)
            .addPathSegment("${catalog.id}/${extraSegment(skip)}.json")

        val root = fetchJson(builder.build())
        return root.arrayOrNull("metas").elements()
            .mapNotNull { it.asObjectOrNull()?.toMetaPreview() }
    }

    /** Lista las fuentes disponibles para un ítem (película o serie). */
    suspend fun streams(addonUrl: String, addonName: String, type: String, id: String): List<StreamSource> {
        val url = baseUrl(addonUrl).newBuilder()
            .addPathSegment("stream")
            .addPathSegment(type)
            .addPathSegment("$id.json")
            .build()

        val root = fetchJson(url)
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
            AddonCatalog(
                type = type,
                id = id,
                name = obj.stringOrNull("name") ?: id
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
            type = stringOrNull("type") ?: "movie",
            name = name,
            poster = stringOrNull("poster"),
            background = stringOrNull("background"),
            releaseInfo = stringOrNull("releaseInfo") ?: stringOrNull("year"),
            description = stringOrNull("description")
        )
    }

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

    private suspend fun fetchJson(url: HttpUrl): JsonObject = withContext(Dispatchers.IO) {
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

    private fun baseUrl(addonUrl: String): HttpUrl =
        addonUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw AddonException("URL de addon inválida: $addonUrl")

    /** Stremio espera `skip=0` como parte del último segmento de la ruta. */
    private fun extraSegment(skip: Int): String = "skip=$skip"

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.intOrNull(name: String): Int? =
        get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun com.google.gson.JsonElement.asObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonArray?.elements(): List<com.google.gson.JsonElement> = this?.toList() ?: emptyList()

    private fun JsonObject.stringList(name: String): List<String> =
        arrayOrNull(name).elements().mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }

    companion object {
        const val DEFAULT_LIMIT = 24
        private val SEEDERS_REGEX = Regex("""👤\s*(\d+)""")

        private fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
