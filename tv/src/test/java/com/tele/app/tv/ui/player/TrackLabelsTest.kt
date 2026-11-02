package com.tele.app.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Etiquetas del menú de pistas.
 *
 * El caso que se protege es el real del KM6: un MKV con dos audios (AC3 5.1 en
 * español y AAC estéreo en inglés) y tres subtítulos. Si las etiquetas se
 * repiten o dicen "Desconocido", elegir pista se vuelve adivinanza.
 */
class TrackLabelsTest {

    @Test
    fun `traduce los codigos ISO a nombre en espanol`() {
        assertEquals("Español", TrackLabels.languageName("es"))
        assertEquals("Español", TrackLabels.languageName("spa"))
        assertEquals("Inglés", TrackLabels.languageName("EN"))
        assertEquals("Desconocido", TrackLabels.languageName(null))
        assertEquals("Desconocido", TrackLabels.languageName("und"))
    }

    @Test
    fun `idioma no listado se resuelve por Locale`() {
        // "nl" no está en la tabla; Locale lo traduce igual.
        assertEquals("Neerlandés", TrackLabels.languageName("nl"))
    }

    @Test
    fun `etiqueta de audio con idioma codec y canales`() {
        assertEquals(
            "Español · AC3 · 5.1",
            TrackLabels.audio(language = "es", mimeType = "audio/ac3", channelCount = 6, trackNumber = 1)
        )
        assertEquals(
            "Inglés · AAC · Estéreo",
            TrackLabels.audio(language = "eng", mimeType = "audio/mp4a-latm", channelCount = 2, trackNumber = 2)
        )
    }

    @Test
    fun `audio sin datos no queda vacio`() {
        assertEquals(
            "Pista 3",
            TrackLabels.audio(language = null, mimeType = null, channelCount = -1, trackNumber = 3)
        )
        assertEquals(
            "Pista 1 · DTS",
            TrackLabels.audio(language = "und", mimeType = "audio/vnd.dts", channelCount = 0, trackNumber = 1)
        )
    }

    @Test
    fun `dos pistas del mismo idioma se distinguen`() {
        val primera = TrackLabels.audio("es", "audio/ac3", 6, 1)
        val segunda = TrackLabels.audio("es", "audio/mp4a-latm", 2, 2)
        assertEquals("Español · AC3 · 5.1", primera)
        assertEquals("Español · AAC · Estéreo", segunda)
        assert(primera != segunda)
    }

    @Test
    fun `subtitulo con idioma y nombre propio`() {
        assertEquals(
            "Español (Latino)",
            TrackLabels.subtitle("es", "Latino", isForced = false, isSdh = false, trackNumber = 1)
        )
    }

    @Test
    fun `subtitulo marca forzado y sdh`() {
        assertEquals(
            "Español [Forzado]",
            TrackLabels.subtitle("es", null, isForced = true, isSdh = false, trackNumber = 1)
        )
        assertEquals(
            "Inglés [SDH]",
            TrackLabels.subtitle("en", null, isForced = false, isSdh = true, trackNumber = 2)
        )
        // Si el nombre ya lo dice, no se repite.
        assertEquals(
            "Español [Forzado]",
            TrackLabels.subtitle("es", "Forzado", isForced = true, isSdh = false, trackNumber = 1)
        )
    }

    @Test
    fun `subtitulo sin idioma usa el numero de pista`() {
        assertEquals(
            "Pista 4",
            TrackLabels.subtitle(null, null, isForced = false, isSdh = false, trackNumber = 4)
        )
    }

    @Test
    fun `boton de audio resume la seleccion`() {
        assertEquals("Audio: sin datos", TrackLabels.shortAudio(emptyList(), null))
        assertEquals("Audio: Español · AC3", TrackLabels.shortAudio(listOf("Español · AC3"), null))
        assertEquals(
            "Audio: Inglés · AAC (2)",
            TrackLabels.shortAudio(listOf("Español · AC3", "Inglés · AAC"), selectedIndex = 1)
        )
    }
}
