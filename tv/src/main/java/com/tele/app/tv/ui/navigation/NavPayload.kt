package com.tele.app.tv.ui.navigation

import com.tele.app.data.addon.model.MetaPreview

/** Lo que el reproductor necesita para arrancar un torrent. */
data class PlaybackRequest(
    val magnetUri: String,
    val fileIndex: Int?,
    val title: String?,
    val mediaType: String? = null,
    val mediaId: String? = null,
    val localFilePath: String? = null
)

/**
 * Paso de datos entre pantallas sin meter objetos grandes ni URIs con
 * caracteres especiales en los argumentos de navegación (un magnet trae `&`,
 * `:` y `%`, que en una ruta se rompen con facilidad).
 */
object NavPayload {
    var selectedMeta: MetaPreview? = null
    var playback: PlaybackRequest? = null
    var initialSearchQuery: String? = null
}
