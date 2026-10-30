package com.tele.app.data.enrich

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiUrlsTest {

    @Test
    fun `solo se habla con wikimedia`() {
        assertEquals(
            setOf("www.wikidata.org", "es.wikipedia.org", "en.wikipedia.org"),
            WikiUrls.allowedHosts
        )
    }

    @Test
    fun `la busqueda por imdb codifica el id`() {
        val url = WikiUrls.imdbSearch("tt0903747")

        assertTrue(url.startsWith("https://www.wikidata.org/w/api.php?"))
        assertTrue(url.contains("haswbstatement%3AP345%3Dtt0903747"))
        assertTrue(url.contains("srlimit=1"))
    }

    @Test
    fun `las etiquetas van en un solo request`() {
        val url = WikiUrls.labels(listOf("Q4500", "Q271050"))

        assertTrue(url.contains("ids=Q4500%7CQ271050"))
        assertTrue(url.contains("languages=es%7Cen"))
    }

    @Test
    fun `el resumen usa ruta codificada sin mas`() {
        val url = WikiUrls.summary("es", "The Matrix")

        assertEquals(
            "https://es.wikipedia.org/api/rest_v1/page/summary/The_Matrix",
            url
        )
    }

    @Test
    fun `un titulo con espacios y parentesis queda codificado`() {
        val url = WikiUrls.summary("en", "The Matrix (film)")

        assertTrue(url.contains("The_Matrix_%28film%29"))
        assertFalse(url.contains("+"))
    }

    @Test
    fun `el user agent se identifica y no se puede confundir con un navegador`() {
        assertTrue(WikiUrls.USER_AGENT.startsWith("TeleTV/"))
        assertTrue(WikiUrls.USER_AGENT.contains("github.com/alexdechile/tele-tv"))
    }
}
