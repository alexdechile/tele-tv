package com.tele.app.core.torrent

/** Un subtítulo externo que viene dentro del mismo torrent que el video. */
data class TorrentSubtitle(
    /** Ruta corta que sirve el servidor local (`/sub/0`). */
    val url: String,
    /** Nombre del archivo dentro del torrent (para depurar). */
    val fileName: String,
    /** Texto para el menú del reproductor: "Español · SRT". */
    val label: String,
    /** Código ISO 639-1 deducido del nombre, o `null` si no se pudo. */
    val language: String?,
    /** Content-Type que espera ExoPlayer (`application/x-subrip`, `text/vtt`…). */
    val contentType: String
)

/**
 * Subtítulos que vienen sueltos en el torrent (`.srt`, `.vtt`, `.ass`).
 *
 * Muchos releases no traen subtítulos embebidos: traen un `.srt` al lado del
 * video, con el idioma escrito en el nombre (`Pelicula.2023.es.srt`). El motor
 * los detecta, los deja bajar y el servidor local los sirve para que ExoPlayer
 * los cargue como si fueran un archivo más.
 *
 * **Nada de microDVD (`.sub`):** Media3 no trae parser para ese formato; ofrecerlo
 * en el menú sería prometer algo que no se va a ver.
 */
object TorrentSubtitles {

    private val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")

    /** Tope de la lista del menú: un release raro no puede tapar la película. */
    const val MAX_SUBTITLES = 12

    fun isSubtitle(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Content-Type que ExoPlayer entiende para cada formato soportado. */
    fun contentTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    /** Nombre del formato para mostrar: "SRT", "VTT", "ASS". */
    fun formatNameOf(fileName: String): String =
        fileName.substringAfterLast('.', "").uppercase().ifEmpty { "SRT" }

    /**
     * Código ISO 639-1 deducido del nombre del archivo.
     *
     * Los nombres de release ponen el título primero y el idioma después
     * (`Pelicula.2023.1080p.es.srt`). Por eso el orden de búsqueda es:
     *
     *  1. el token justo antes de la extensión (`...es.srt`);
     *  2. el resto de los tokens **salteando el primero**, que es el título.
     *
     * Ese salteo importa por las siglas de dos letras que también son palabras:
     * `It.2017.srt` es la película *It*, no subtítulos italianos, y
     * `De.La.Nada.srt` no es alemán. Si el archivo es solo el código (`es.srt`),
     * se acepta.
     */
    fun languageOf(fileName: String): String? {
        val tokens = tokenize(fileName.substringBeforeLast('.', fileName))
        if (tokens.isEmpty()) return null
        if (tokens.size == 1) return LANGUAGE_ALIASES[tokens[0]]

        LANGUAGE_ALIASES[tokens.last()]?.let { return it }
        for (token in tokens.drop(1)) {
            LANGUAGE_ALIASES[token]?.let { return it }
        }
        return null
    }

    /** Texto del menú: idioma legible + formato, con las marcas que importan. */
    fun labelOf(fileName: String): String {
        val language = languageOf(fileName)
        val languageName = language?.let { SPANISH_NAMES[it] } ?: "Desconocido"

        val base = fileName.substringBeforeLast('.', fileName).lowercase()
        val tags = buildList {
            if ("forced" in base || "forzado" in base) add("Forzado")
            if ("sdh" in base || base.contains("cc")) add("SDH")
        }

        val head = if (tags.isEmpty()) {
            languageName
        } else {
            "$languageName [${tags.joinToString(", ")}]"
        }
        return "$head · ${formatNameOf(fileName)}"
    }

    private fun tokenize(value: String): List<String> =
        value.lowercase()
            .split('.', '_', '-', ' ', '[', ']', '(', ')', '{', '}')
            .filter { it.isNotBlank() }

    /** Códigos ISO 639-2/3 y nombres en inglés que aparecen en los releases. */
    private val LANGUAGE_ALIASES: Map<String, String> = buildMap {
        fun register(code: String, vararg aliases: String) {
            put(code, code)
            for (alias in aliases) put(alias, code)
        }
        register("es", "spa", "esp", "spanish", "espanol", "castellano", "latino", "es-la", "es-419")
        register("en", "eng", "english", "ingles", "en-us", "en-gb")
        register("pt", "por", "portuguese", "portugues", "pt-br", "pt-pt", "brazilian")
        register("fr", "fre", "fra", "french", "frances")
        register("it", "ita", "italian", "italiano")
        register("de", "ger", "deu", "german", "aleman")
        register("ja", "jpn", "japanese", "japones")
        register("ko", "kor", "korean", "coreano")
        register("zh", "chi", "zho", "chinese", "chino", "mandarin")
        register("ru", "rus", "russian", "ruso")
        register("pl", "pol", "polish", "polaco")
        register("nl", "dut", "nld", "dutch", "holandes")
        register("sv", "swe", "swedish", "sueco")
        register("da", "dan", "danish", "danes")
        register("no", "nor", "norwegian", "noruego")
        register("fi", "fin", "finnish", "finlandes")
        register("tr", "tur", "turkish", "turco")
        register("ar", "ara", "arabic", "arabe")
        register("hi", "hin", "hindi")
    }

    private val SPANISH_NAMES: Map<String, String> = mapOf(
        "es" to "Español",
        "en" to "Inglés",
        "pt" to "Portugués",
        "fr" to "Francés",
        "it" to "Italiano",
        "de" to "Alemán",
        "ja" to "Japonés",
        "ko" to "Coreano",
        "zh" to "Chino",
        "ru" to "Ruso",
        "pl" to "Polaco",
        "nl" to "Holandés",
        "sv" to "Sueco",
        "da" to "Danés",
        "no" to "Noruego",
        "fi" to "Finlandés",
        "tr" to "Turco",
        "ar" to "Árabe",
        "hi" to "Hindi"
    )
}
