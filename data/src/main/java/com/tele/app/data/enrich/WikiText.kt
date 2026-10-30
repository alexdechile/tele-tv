package com.tele.app.data.enrich

/**
 * Normaliza texto que llega de Wikipedia antes de que toque la pantalla.
 *
 * Aunque la API REST entrega `extract` en texto plano, acá se limpia igual:
 * si algún día llega HTML o una entidad rara, no se dibuja markup ni se
 * arrastra un enlace `javascript:`. La UI solo recibe `String` cortado.
 */
object WikiText {

    private val TAG = Regex("<[^>]{0,300}>")
    private val SPACES = Regex("\\s+")

    fun clean(raw: String?, maxChars: Int = 600): String? {
        if (raw.isNullOrBlank()) return null

        var text = TAG.replace(raw, " ")
        text = text
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            // &amp; va al final para no decodificar dos veces ("&amp;lt;" no es "<").
            .replace("&amp;", "&")

        text = text.filter { character -> character == '\n' || character == '\t' || !character.isISOControl() }
        text = SPACES.replace(text, " ").trim()

        return if (text.isEmpty()) null else truncate(text, maxChars)
    }

    /** Corta en punto seguido si se puede; si no, en la última palabra. */
    private fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text

        val window = text.substring(0, maxChars)
        val sentence = window.lastIndexOf(". ")
        if (sentence > maxChars / 3) return window.substring(0, sentence + 1)

        val space = window.lastIndexOf(' ')
        val end = if (space > maxChars / 2) space else maxChars
        return window.substring(0, end).trimEnd(' ', ',', ';') + "…"
    }
}
