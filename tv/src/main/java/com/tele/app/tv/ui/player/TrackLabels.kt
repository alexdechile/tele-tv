package com.tele.app.tv.ui.player

import java.util.Locale

/**
 * Cómo se llaman las pistas en el menú del reproductor.
 *
 * Vive aparte de [PlayerScreen] a propósito: es lógica pura (un `Format` de
 * Media3 es solo datos) y así se puede probar en la JVM sin emulador. Lo que
 * hay que proteger es concreto: que un español AC3 5.1 no se llame "Desconocido"
 * y que dos pistas distintas no terminen con la misma etiqueta en el menú.
 */
object TrackLabels {

    /** Nombre del idioma en español a partir del código ISO del contenedor. */
    fun languageName(lang: String?): String {
        if (lang.isNullOrBlank() || lang == "und") return "Desconocido"
        val lower = lang.lowercase().trim()
        return when {
            lower in setOf("es", "spa", "es-419", "es-es", "es-la") -> "Español"
            lower in setOf("en", "eng", "en-us", "en-gb") -> "Inglés"
            lower in setOf("pt", "por", "pt-br", "pt-pt") -> "Portugués"
            lower in setOf("fr", "fra", "fre") -> "Francés"
            lower in setOf("it", "ita") -> "Italiano"
            lower in setOf("de", "deu", "ger") -> "Alemán"
            lower in setOf("ja", "jpn") -> "Japonés"
            lower in setOf("ko", "kor") -> "Coreano"
            lower in setOf("zh", "zho", "chi") -> "Chino"
            lower in setOf("ru", "rus") -> "Ruso"
            else -> runCatching {
                Locale.forLanguageTag(lower)
                    .getDisplayLanguage(Locale("es"))
                    .replaceFirstChar { it.uppercase() }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: lower.uppercase()
        }
    }

    /** "5.1", "Estéreo", "Mono"… o `null` si el contenedor no lo dice. */
    fun channelsName(channelCount: Int): String? = when (channelCount) {
        1 -> "Mono"
        2 -> "Estéreo"
        6 -> "5.1"
        8 -> "7.1"
        in 3..8 -> "$channelCount canales"
        else -> null
    }

    /** Nombre corto del códec a partir del `mimeType` de Media3. */
    fun codecName(mimeType: String?): String? = when (mimeType?.lowercase()) {
        "audio/ac3" -> "AC3"
        "audio/eac3" -> "E-AC3"
        "audio/eac3-joc" -> "E-AC3 JOC"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/true-hd" -> "TrueHD"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/mpeg" -> "MP3"
        else -> null
    }

    /**
     * Etiqueta completa de una pista de audio: idioma + códec + canales.
     *
     * Ejemplo: `Español · AC3 · 5.1`.
     */
    fun audio(
        language: String?,
        mimeType: String?,
        channelCount: Int,
        trackNumber: Int
    ): String {
        val languageLabel = languageName(language)
        val head = if (languageLabel != "Desconocido") languageLabel else "Pista $trackNumber"
        val detail = listOfNotNull(codecName(mimeType), channelsName(channelCount))
            .joinToString(" · ")
        return if (detail.isEmpty()) head else "$head · $detail"
    }

    /**
     * Etiqueta de un subtítulo: idioma, nombre propio si aporta algo, y las
     * marcas que cambian lo que uno ve (forzado, SDH).
     */
    fun subtitle(
        language: String?,
        customLabel: String?,
        isForced: Boolean,
        isSdh: Boolean,
        trackNumber: Int
    ): String {
        val languageLabel = languageName(language)
        val own = customLabel?.trim()?.takeIf { it.isNotBlank() }

        // Hay releases que ponen "Forzado" o "SDH" como nombre de la pista. Eso
        // no es un nombre propio: es una marca, y va entre corchetes como las
        // demás, no entre paréntesis como si fuera un idioma.
        val ownLower = own?.lowercase().orEmpty()
        val ownMeansForced = ownLower.contains("forz") || ownLower == "forced"
        val ownMeansSdh = ownLower.contains("sdh") || ownLower == "cc"

        val base = when {
            own != null && !ownMeansForced && !ownMeansSdh &&
                !own.equals(languageLabel, ignoreCase = true) -> {
                if (languageLabel != "Desconocido") "$languageLabel ($own)" else own
            }
            languageLabel != "Desconocido" -> languageLabel
            else -> "Pista $trackNumber"
        }

        val tags = buildList {
            if ((isForced || ownMeansForced) && !base.contains("forz", ignoreCase = true)) add("Forzado")
            if ((isSdh || ownMeansSdh) && !base.contains("sdh", ignoreCase = true)) add("SDH")
        }

        return if (tags.isEmpty()) base else "$base [${tags.joinToString(", ")}]"
    }

    /** Texto corto para el botón de la barra: `Audio: Español · AC3 (2)`. */
    fun shortAudio(labels: List<String>, selectedIndex: Int?): String = when {
        labels.isEmpty() -> "Audio: sin datos"
        labels.size == 1 -> "Audio: ${labels.first()}"
        else -> {
            val chosen = selectedIndex?.let { labels.getOrNull(it) } ?: labels.first()
            "Audio: $chosen (${labels.size})"
        }
    }
}
