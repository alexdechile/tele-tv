package com.tele.app.data.enrich

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Consulta anónima a JustWatch vía GraphQL público para conocer la disponibilidad
 * en plataformas de streaming (Filmin, MUBI, Netflix, etc.) en España (ES).
 *
 * Sin API keys, sin registro, sin cookies de tracking.
 */
class JustWatchClient(
    private val http: OkHttpClient = defaultHttpClient()
) {

    suspend fun getPlatforms(
        title: String,
        imdbId: String? = null,
        country: String = DEFAULT_COUNTRY
    ): List<String> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()

        val jsonBody = buildQuery(title, country)
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.justwatch.com/")
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parsePlatforms(body, imdbId)
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parsePlatforms(jsonText: String, expectedImdbId: String?): List<String> {
        val root = try {
            JsonParser.parseString(jsonText).asJsonObject
        } catch (_: Exception) {
            return emptyList()
        }

        val edges = root.getAsJsonObject("data")
            ?.getAsJsonObject("popularTitles")
            ?.getAsJsonArray("edges") ?: return emptyList()

        var matchedNode: JsonObject? = null

        // Buscar coincidencia exacta por IMDb ID si se proporcionó
        if (!expectedImdbId.isNullOrBlank()) {
            for (edge in edges) {
                val node = edge.asJsonObject?.getAsJsonObject("node") ?: continue
                val nodeImdb = node.getAsJsonObject("content")
                    ?.getAsJsonObject("externalIds")
                    ?.get("imdbId")?.asString
                if (nodeImdb.equals(expectedImdbId, ignoreCase = true)) {
                    matchedNode = node
                    break
                }
            }
        }

        // Si no coincidió por IMDb ID, tomar el primer resultado
        if (matchedNode == null && edges.size() > 0) {
            matchedNode = edges.get(0).asJsonObject?.getAsJsonObject("node")
        }

        val offers = matchedNode?.getAsJsonArray("offers") ?: return emptyList()

        val platforms = mutableListOf<String>()
        for (offer in offers) {
            val obj = offer.asJsonObject ?: continue
            val mtype = obj.get("monetizationType")?.asString
            // Nos interesan suscripciones (FLATRATE) y gratuitas (FREE)
            if (mtype == "FLATRATE" || mtype == "FREE") {
                val pkg = obj.getAsJsonObject("package") ?: continue
                val rawName = pkg.get("clearName")?.asString ?: pkg.get("shortName")?.asString ?: continue
                val clean = normalizePlatform(rawName)
                if (clean != null && clean !in platforms) {
                    platforms.add(clean)
                }
            }
        }

        return platforms
    }

    private fun normalizePlatform(raw: String): String? {
        val lower = raw.lowercase()
        return when {
            lower.contains("filmin") -> "Filmin"
            lower.contains("mubi") -> "MUBI"
            lower.contains("netflix") -> "Netflix"
            lower.contains("prime video") || lower.contains("amazon prime") -> "Prime Video"
            lower.contains("disney") -> "Disney+"
            lower.contains("apple tv") -> "Apple TV+"
            lower.contains("hbo") || lower.contains("max") -> "Max"
            lower.contains("movistar") -> "Movistar Plus+"
            lower.contains("rtve") -> "RTVE Play"
            lower.contains("flixolé") || lower.contains("flixole") -> "FlixOlé"
            lower.contains("paramount") -> "Paramount+"
            else -> raw.trim().takeIf { it.isNotBlank() }
        }
    }

    private fun buildQuery(title: String, country: String): String {
        val escapedTitle = title.replace("\"", "\\\"")
        return """
        {
            "query": "query GetSearchTitles(${'$'}searchTitlesFilter: TitleFilter!, ${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}filter: OfferFilter!) { popularTitles(country: ${'$'}country, filter: ${'$'}searchTitlesFilter, first: ${'$'}first, sortBy: POPULAR) { edges { node { id content(country: ${'$'}country, language: ${'$'}language) { title originalReleaseYear externalIds { imdbId } } offers(country: ${'$'}country, platform: WEB, filter: ${'$'}filter) { monetizationType package { clearName shortName } } } } } }",
            "variables": {
                "country": "$country",
                "language": "es",
                "first": 4,
                "searchTitlesFilter": {
                    "searchQuery": "$escapedTitle"
                },
                "filter": {
                    "bestOnly": true
                }
            }
        }
        """.trimIndent()
    }

    companion object {
        const val GRAPHQL_ENDPOINT = "https://apis.justwatch.com/graphql"
        const val DEFAULT_COUNTRY = "ES"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}
