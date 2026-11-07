package com.tele.app.tv.ui.detail

import com.tele.app.data.addon.model.StreamSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedSubtitlesTest {

    @Test
    fun `detecta releases con subtitulos pegados en varias variantes`() {
        val testCases = listOf(
            "Movie.2024.1080p.WEBRip.x264.KORSUB-GROUP",
            "Movie.2024.720p.HD-CAM.HC.x264",
            "Movie.2024.1080p.HDRip.HARDCODED.ENG.SUBS",
            "Movie (2024) [KOR-SUB] 1080p WEB-DL",
            "Series.S01E01.SUBBED.720p.HDTV",
            "Film.2023.1080p.WEBRip.ENSUBBED-EVO",
            "Some.Movie.2024.1080p.HC-Rip.x264"
        )

        for (detail in testCases) {
            val stream = StreamSource(
                addon = "Torrentio",
                label = "1080p",
                detail = detail
            )
            assertTrue("Debería detectar hardcoded en: $detail", stream.hasHardcodedSubtitles())
        }
    }

    @Test
    fun `no confunde palabras comunes con hardcoded`() {
        val cleanCases = listOf(
            "Movie.2024.1080p.WEBRip.x264.DTS-HD.MA.5.1",
            "Movie.2024.1080p.BluRay.x265.10bit",
            "Match.Point.2005.1080p.BluRay",
            "The.Catch.S01E01.720p.HDTV"
        )

        for (detail in cleanCases) {
            val stream = StreamSource(
                addon = "Torrentio",
                label = "1080p",
                detail = detail
            )
            assertFalse("No debería marcar hardcoded en: $detail", stream.hasHardcodedSubtitles())
        }
    }
}
