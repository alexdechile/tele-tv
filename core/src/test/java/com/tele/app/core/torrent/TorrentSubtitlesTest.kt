package com.tele.app.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detección de los subtítulos sueltos que vienen en el torrent.
 *
 * Si esto se equivoca, la app ofrece en el menú un archivo que no es un
 * subtítulo, o pierde el español de un release porque el idioma estaba en el
 * nombre y nadie lo leyó.
 */
class TorrentSubtitlesTest {

    @Test
    fun `reconoce los formatos que Media3 si sabe leer`() {
        assertTrue(TorrentSubtitles.isSubtitle("Pelicula.es.srt"))
        assertTrue(TorrentSubtitles.isSubtitle("Pelicula.ES.VTT"))
        assertTrue(TorrentSubtitles.isSubtitle("Pelicula.ass"))
        assertTrue(TorrentSubtitles.isSubtitle("Pelicula.en.ssa"))
    }

    @Test
    fun `no confunde video ni basura con subtitulos`() {
        assertFalse(TorrentSubtitles.isSubtitle("Pelicula.mkv"))
        assertFalse(TorrentSubtitles.isSubtitle("poster.jpg"))
        // microDVD: Media3 no trae parser, no se ofrece.
        assertFalse(TorrentSubtitles.isSubtitle("Pelicula.es.sub"))
        assertFalse(TorrentSubtitles.isSubtitle("sin-extension"))
    }

    @Test
    fun `content type acorde al formato`() {
        assertEquals("application/x-subrip", TorrentSubtitles.contentTypeOf("x.srt"))
        assertEquals("text/vtt", TorrentSubtitles.contentTypeOf("x.vtt"))
        assertEquals("text/x-ssa", TorrentSubtitles.contentTypeOf("x.ass"))
    }

    @Test
    fun `lee el idioma del nombre del archivo`() {
        assertEquals("es", TorrentSubtitles.languageOf("The.Movie.2023.1080p.es.srt"))
        assertEquals("es", TorrentSubtitles.languageOf("The.Movie.Spanish.srt"))
        assertEquals("es", TorrentSubtitles.languageOf("The Movie Castellano.srt"))
        assertEquals("en", TorrentSubtitles.languageOf("The.Movie.eng.srt"))
        assertEquals("pt", TorrentSubtitles.languageOf("The.Movie.PT-BR.srt"))
        assertEquals("de", TorrentSubtitles.languageOf("Der.Film.German.vtt"))
    }

    @Test
    fun `sin idioma reconocible no inventa uno`() {
        assertNull(TorrentSubtitles.languageOf("subtitulos.srt"))
        assertNull(TorrentSubtitles.languageOf("The.Movie.2023.srt"))
    }

    @Test
    fun `el nombre de la pelicula no se toma como idioma`() {
        // "It" es película y también código de italiano: el token es la palabra
        // suelta, y acá el archivo no declara idioma.
        assertNull(TorrentSubtitles.languageOf("It.2017.srt"))
    }

    @Test
    fun `la etiqueta del menu dice idioma y formato`() {
        assertEquals("Español · SRT", TorrentSubtitles.labelOf("Pelicula.es.srt"))
        assertEquals("Inglés · VTT", TorrentSubtitles.labelOf("Pelicula.en.vtt"))
        assertEquals("Desconocido · SRT", TorrentSubtitles.labelOf("subtitulos.srt"))
    }

    @Test
    fun `la etiqueta marca forzado y sdh`() {
        assertEquals("Español [Forzado] · SRT", TorrentSubtitles.labelOf("Pelicula.es.forced.srt"))
        assertEquals("Español [SDH] · SRT", TorrentSubtitles.labelOf("Pelicula.es.sdh.srt"))
        assertEquals("Español [Forzado, SDH] · SRT", TorrentSubtitles.labelOf("Pelicula.es.forced.sdh.srt"))
    }
}
