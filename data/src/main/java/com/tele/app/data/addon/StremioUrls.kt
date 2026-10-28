package com.tele.app.data.addon

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/**
 * Construcción de las URLs del protocolo de addons de Stremio (v3).
 *
 * La parte delicada es el segmento de `extra`. El protocolo lo define como un
 * único segmento de ruta con pares `clave=valor` separados por `&`:
 *
 *     /catalog/movie/top/search=the%20matrix&genre=Action&skip=0.json
 *
 * Si ese segmento se escapa (por ejemplo con `HttpUrl.addPathSegment`, que
 * convierte `/` en `%2F`), el addon **no ve los extras** y devuelve el catálogo
 * por defecto. Eso compila, responde 200 y muestra contenido: el fallo es
 * silencioso. Por eso las URLs se arman como texto y se codifica *solo* el valor
 * de cada extra (espacios, `&`, `?`, acentos), nunca la estructura.
 */
object StremioUrls {

    fun manifest(baseUrl: String): String = "${normalize(baseUrl)}/manifest.json"

    fun catalog(
        baseUrl: String,
        type: String,
        catalogId: String,
        extras: Map<String, String> = emptyMap()
    ): String {
        val extraSegment = extras.entries
            .filter { (_, value) -> value.isNotBlank() }
            .joinToString("&") { (key, value) -> "$key=${encodeExtraValue(value)}" }

        val path = if (extraSegment.isEmpty()) catalogId else "$catalogId/$extraSegment"
        return "${normalize(baseUrl)}/catalog/$type/$path.json"
    }

    fun meta(baseUrl: String, type: String, id: String): String =
        "${normalize(baseUrl)}/meta/$type/${encodePathId(id)}.json"

    fun stream(baseUrl: String, type: String, id: String): String =
        "${normalize(baseUrl)}/stream/$type/${encodePathId(id)}.json"

    /**
     * Codifica el valor de un extra. `URLEncoder` usa `+` para el espacio, que en
     * una ruta no significa "espacio": se reemplaza por `%20`.
     */
    fun encodeExtraValue(value: String): String = urlEncode(value)

    /**
     * El id viaja como segmento de ruta (`tt0903747:1:1`).
     *
     * Los dos puntos **no** se codifican: son válidos en un segmento de ruta y el
     * ecosistema Stremio los usa crudos. Se codifica cada parte por separado para
     * que un id raro (con espacios o `/`) no rompa la URL. Cinemeta y Torrentio
     * aceptan `%3A` igual, pero así los logs se leen.
     */
    private fun encodePathId(id: String): String = id.split(":").joinToString(":") { urlEncode(it) }

    private fun normalize(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        requireNotNull(trimmed.toHttpUrlOrNull()) { "URL de addon inválida: $baseUrl" }
        return trimmed
    }

    private fun urlEncode(value: String): String = try {
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    } catch (error: Exception) {
        // UTF-8 siempre existe; esto es por si alguna JVM rara lo reporta.
        value
    }
}
