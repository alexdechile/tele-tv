package com.tele.app.core.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TorrentEngineKeepDeleteTest {

    @Test
    fun `keepOnStop por defecto es false y se puede conmutar`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "tele-keep-test")
        val engine = TorrentEngine(saveDir = tempDir)

        assertFalse(engine.isKeepDownloaded())
        engine.setKeepDownloaded(true)
        assertTrue(engine.isKeepDownloaded())
        engine.setKeepDownloaded(false)
        assertFalse(engine.isKeepDownloaded())
    }
}
