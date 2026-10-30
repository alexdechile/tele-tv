package com.tele.app.data.enrich

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP de Wikidata/Wikipedia, con las defensas del módulo:
 *
 *  - **Solo HTTPS** (las URLs se arman en [WikiUrls]).
 *  - **Allowlist de hosts**: si la respuesta termina en otro dominio (redirect,
 *    DNS raro), se descarta. Nada de SSRF hacia la red local.
 *  - **Tope de tamaño** ([MAX_BODY_BYTES]): una respuesta gigante no puede
 *    reventar la memoria del TV box.
 *  - **Timeouts cortos**: si Wikimedia no responde, la ficha simplemente no
 *    aparece; la pantalla nunca se queda esperando.
 *  - **User-Agent identificable**, como exige la política de Wikimedia.
 */
class WikiClient(
    private val http: OkHttpClient = defaultHttpClient()
) {

    suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
        val host = runCatching { URI(url).host }.getOrNull() ?: return@withContext null
        if (host !in WikiUrls.allowedHosts) return@withContext null

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WikiUrls.USER_AGENT)
            .header("Accept", "application/json")
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                // Un redirect puede cambiar de host: se valida el host final.
                if (response.request.url.host !in WikiUrls.allowedHosts) return@withContext null

                val body = response.body ?: return@withContext null
                val source = body.source()
                source.request(MAX_BODY_BYTES + 1)
                if (source.buffer.size > MAX_BODY_BYTES) return@withContext null

                val text = body.string()
                if (text.isBlank()) return@withContext null

                JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            }
        } catch (error: IOException) {
            null
        } catch (error: JsonParseException) {
            null
        } catch (error: IllegalStateException) {
            null
        }
    }

    private companion object {
        /** ~1 MB: las entidades grandes rondan los 300 KB. */
        const val MAX_BODY_BYTES = 1_000_000L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
