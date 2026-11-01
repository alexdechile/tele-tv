package com.tele.app.data.addon.model

/**
 * País de producción o procedencia de una película o serie.
 *
 * Se usa tanto para filtrar en el buscador como para traducir las etiquetas
 * de países que devuelve Cinemeta (en inglés, ej. "United States", "Spain")
 * a español ("Estados Unidos", "España").
 */
data class Country(
    val code: String,
    val nameEs: String,
    val englishNames: List<String>,
    val wikidataQid: String
) {
    /**
     * Verifica si una cadena de países de Cinemeta o Wikidata coincide con este país.
     * Soporta nombres en español, nombres en inglés y listas separadas por coma.
     */
    fun matches(rawCountry: String?): Boolean {
        if (rawCountry.isNullOrBlank()) return false
        val parts = rawCountry.split(",").map { it.trim().lowercase() }
        val targets = (listOf(nameEs.lowercase()) + englishNames.map { it.lowercase() })
        return parts.any { part ->
            targets.any { target ->
                part == target || part.contains(target) || target.contains(part)
            }
        }
    }

    companion object {
        val SPAIN = Country("ES", "España", listOf("Spain"), "Q29")
        val CHILE = Country("CL", "Chile", listOf("Chile"), "Q298")
        val ARGENTINA = Country("AR", "Argentina", listOf("Argentina"), "Q414")
        val MEXICO = Country("MX", "México", listOf("Mexico"), "Q96")
        val USA = Country("US", "Estados Unidos", listOf("United States", "USA", "United States of America", "U.S.", "U.S.A."), "Q30")
        val COLOMBIA = Country("CO", "Colombia", listOf("Colombia"), "Q739")
        val UK = Country("GB", "Reino Unido", listOf("United Kingdom", "UK", "Great Britain", "England", "Scotland"), "Q145")
        val FRANCE = Country("FR", "Francia", listOf("France"), "Q142")
        val ITALY = Country("IT", "Italia", listOf("Italy"), "Q38")
        val GERMANY = Country("DE", "Alemania", listOf("Germany"), "Q183")
        val JAPAN = Country("JP", "Japón", listOf("Japan"), "Q17")
        val SOUTH_KOREA = Country("KR", "Corea del Sur", listOf("South Korea", "Korea", "Republic of Korea"), "Q884")
        val BRAZIL = Country("BR", "Brasil", listOf("Brazil"), "Q155")
        val CANADA = Country("CA", "Canadá", listOf("Canada"), "Q16")
        val AUSTRALIA = Country("AU", "Australia", listOf("Australia"), "Q408")
        val PERU = Country("PE", "Perú", listOf("Peru"), "Q419")
        val URUGUAY = Country("UY", "Uruguay", listOf("Uruguay"), "Q77")
        val INDIA = Country("IN", "India", listOf("India"), "Q668")
        val IRELAND = Country("IE", "Irlanda", listOf("Ireland"), "Q27")
        val DENMARK = Country("DK", "Dinamarca", listOf("Denmark"), "Q35")
        val SWEDEN = Country("SE", "Suecia", listOf("Sweden"), "Q34")
        val NORWAY = Country("NO", "Noruega", listOf("Norway"), "Q20")

        val SUPPORTED: List<Country> = listOf(
            SPAIN,
            CHILE,
            ARGENTINA,
            MEXICO,
            USA,
            COLOMBIA,
            UK,
            FRANCE,
            ITALY,
            GERMANY,
            JAPAN,
            SOUTH_KOREA,
            BRAZIL,
            CANADA,
            AUSTRALIA,
            PERU,
            URUGUAY,
            INDIA,
            IRELAND,
            DENMARK,
            SWEDEN,
            NORWAY
        )

        /** Traduce una cadena en inglés o cruda (ej. "Mexico, Spain") a español ("México, España"). */
        fun displayLabel(rawCountry: String?): String? {
            if (rawCountry.isNullOrBlank()) return null
            val parts = rawCountry.split(",").map { it.trim() }
            val translated = parts.map { part ->
                SUPPORTED.firstOrNull { it.matches(part) }?.nameEs ?: part
            }
            return translated.distinct().joinToString(", ")
        }
    }
}
