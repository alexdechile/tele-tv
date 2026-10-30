package com.tele.app.data.enrich

import java.net.URLEncoder

/**
 * URLs de Wikidata y Wikipedia, armadas **solo** desde acá.
 *
 * Regla de seguridad del módulo: ninguna URL de este tipo se construye con
 * datos que vengan de un addon o del usuario. El único dato variable es el id
 * de IMDb (validado con `tt` + dígitos) y títulos que devuelve la propia API,
 * siempre codificados. Como además [allowedHosts] se valida en [WikiClient],
 * un redirect inesperado tampoco puede sacar la app de Wikimedia.
 */
object WikiUrls {

    /**
     * Wikimedia exige un User-Agent identificable con contacto; sin eso puede
     * devolver 403. Este es el de la app.
     */
    const val USER_AGENT = "TeleTV/1.0 (Android TV; +https://github.com/alexdechile/tele-tv)"

    private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"

    /** Hosts permitidos para cualquier descarga de este módulo. */
    val allowedHosts: Set<String> = setOf(
        "www.wikidata.org",
        "es.wikipedia.org",
        "en.wikipedia.org"
    )

    /** Busca la entidad de Wikidata cuyo P345 (IMDb) coincide con [imdbId]. */
    fun imdbSearch(imdbId: String): String =
        "$WIKIDATA_API?action=query&list=search" +
            "&srsearch=${encodeQuery("haswbstatement:P345=$imdbId")}" +
            "&srlimit=1&format=json"

    /** Entidad completa (afirmaciones + enlaces a Wikipedia es/en). */
    fun entity(entityId: String): String =
        "$WIKIDATA_API?action=wbgetentities&ids=$entityId" +
            "&props=claims%7Csitelinks&sitefilter=eswiki%7Cenwiki&format=json"

    /** Etiquetas (nombres) y enlaces de varias entidades en una sola llamada. */
    fun labels(entityIds: List<String>): String =
        "$WIKIDATA_API?action=wbgetentities&ids=${entityIds.joinToString("%7C")}" +
            "&props=labels%7Csitelinks&languages=es%7Cen" +
            "&sitefilter=eswiki%7Cenwiki&format=json"

    /** Resumen (primer párrafo) de una página de Wikipedia. */
    fun summary(lang: String, title: String): String =
        "https://$lang.wikipedia.org/api/rest_v1/page/summary/${encodePath(title)}"

    /** URL legible para atribuir la fuente en pantalla. */
    fun pageUrl(lang: String, title: String): String =
        "https://$lang.wikipedia.org/wiki/${encodePath(title)}"

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** En un segmento de ruta el espacio va como %20, no como "+". */
    private fun encodePath(value: String): String =
        URLEncoder.encode(value.replace(' ', '_'), "UTF-8").replace("+", "%20")
}
