package com.tele.app.tv.ui

import com.tele.app.data.addon.AddonRepository
import com.tele.app.data.enrich.WikipediaEnricher

/**
 * Composición de dependencias de la app.
 *
 * Se mantiene deliberadamente simple (sin Hilt) en este milestone: el grafo es
 * un singleton perezoso. Cuando el proyecto crezca, este objeto es el único
 * punto que habrá que migrar a módulos de inyección.
 */
object AppGraph {
    val addons: AddonRepository by lazy { AddonRepository() }

    /**
     * Ficha cultural (Wikidata/Wikipedia). Se comparte una sola instancia para
     * que la caché en memoria sirva a todas las pantallas.
     */
    val wiki: WikipediaEnricher by lazy { WikipediaEnricher() }
}
