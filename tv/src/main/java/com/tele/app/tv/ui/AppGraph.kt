package com.tele.app.tv.ui

import com.tele.app.data.addon.AddonRepository

/**
 * Composición de dependencias de la app.
 *
 * Se mantiene deliberadamente simple (sin Hilt) en este milestone: el grafo es
 * un singleton perezoso. Cuando el proyecto crezca, este objeto es el único
 * punto que habrá que migrar a módulos de inyección.
 */
object AppGraph {
    val addons: AddonRepository by lazy { AddonRepository() }
}
