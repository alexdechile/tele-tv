package com.tele.app.data.addon

import com.tele.app.data.addon.model.yearOfReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del armado de URLs del protocolo.
 *
 * Existen por un bug real: si el segmento de `extra` se escapa como si fuera un
 * path segment (`top%2Fsearch%3Dmatrix.json`), Cinemeta responde 200 con el
 * catálogo por defecto. La búsqueda "funciona" pero devuelve cualquier cosa.
 */
class StremioUrlsTest {

    private val base = "https://v3-cinemeta.strem.io/"

    @Test
    fun `el extra queda como segmento crudo y solo se codifica el valor`() {
        val url = StremioUrls.catalog(
            baseUrl = base,
            type = "movie",
            catalogId = "top",
            extras = linkedMapOf("skip" to "0", "search" to "the matrix")
        )

        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/skip=0&search=the%20matrix.json",
            url
        )
    }

    @Test
    fun `nunca escapa la estructura del segmento de extra`() {
        val url = StremioUrls.catalog(base, "series", "top", mapOf("genre" to "Sci-Fi", "skip" to "50"))

        assertFalse("no debe escapar la barra", url.contains("%2F"))
        assertFalse("no debe escapar el igual", url.contains("%3D"))
        assertFalse("no debe escapar el ampersand estructural", url.contains("%26"))
        assertTrue(url.endsWith("/catalog/series/top/genre=Sci-Fi&skip=50.json"))
    }

    @Test
    fun `un ampersand dentro del texto buscado se codifica`() {
        val url = StremioUrls.catalog(base, "movie", "top", mapOf("search" to "law & order"))

        assertTrue(url, url.endsWith("/search=law%20%26%20order.json"))
    }

    @Test
    fun `sin extras el catalogo no lleva segmento vacio`() {
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top.json",
            StremioUrls.catalog(base, "movie", "top")
        )
    }

    @Test
    fun `un extra vacio se descarta`() {
        val url = StremioUrls.catalog(base, "movie", "top", mapOf("skip" to "0", "search" to "  "))
        assertEquals("https://v3-cinemeta.strem.io/catalog/movie/top/skip=0.json", url)
    }

    @Test
    fun `la ficha de un episodio conserva los dos puntos del id`() {
        val url = StremioUrls.meta(base, "series", "tt0903747:1:1")
        assertTrue(url, url.endsWith("/meta/series/tt0903747:1:1.json"))
    }

    @Test
    fun `las fuentes de un episodio apuntan al id con temporada y episodio`() {
        val url = StremioUrls.stream("https://torrentio.strem.fun/", "series", "tt0903747:1:1")
        assertEquals("https://torrentio.strem.fun/stream/series/tt0903747:1:1.json", url)
    }

    @Test
    fun `el manifest se pide en la raiz`() {
        assertEquals(
            "https://v3-cinemeta.strem.io/manifest.json",
            StremioUrls.manifest(base)
        )
    }

    @Test
    fun `el anio sale del primer valor de releaseInfo`() {
        assertEquals(2019, yearOfReleaseInfo("2019"))
        assertEquals(2011, yearOfReleaseInfo("2011–2019"))
        assertEquals(2014, yearOfReleaseInfo("2014-"))
        assertEquals(1999, yearOfReleaseInfo("1999-2003"))
        assertNull(yearOfReleaseInfo("próximamente"))
    }
}
