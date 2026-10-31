package com.tele.app.tv

import android.app.Application
import com.tele.app.core.torrent.TorrentPaths
import java.io.File

/**
 * Arranque de la app.
 *
 * La TV box no es un disco de descargas: el video se ve y se borra. El borrado
 * normal ocurre al salir del reproductor, pero si la ejecución anterior murió
 * a mitad del video (crash, corte de luz, `adb install`) o el archivo estaba
 * tomado por libtorrent justo en ese momento, quedaban restos en
 * `cacheDir/torrents`. Acá se barren al arrancar, cuando con certeza no hay
 * ningún reproductor leyendo.
 */
class TeleTvApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Thread({
            val root = File(cacheDir, "torrents")
            if (root.exists()) {
                TorrentPaths.purge(root)
            }
        }, "torrent-cache-purge").apply { isDaemon = true }.start()
    }
}
