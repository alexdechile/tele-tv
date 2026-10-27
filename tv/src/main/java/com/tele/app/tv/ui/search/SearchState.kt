package com.tele.app.tv.ui.search

import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.SearchFilters

/**
 * Un resultado del buscador.
 *
 * [rating] y [seasonCount] llegan después, cuando se pide la ficha de cada
 * título: el catálogo solo entrega carátula, nombre y año.
 */
data class SearchItem(
    val meta: MetaPreview,
    val rating: Double? = null,
    val seasonCount: Int? = null
)

/** Estado completo de la pantalla del buscador. */
data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val genres: List<String> = emptyList(),
    val loading: Boolean = false,
    val enriching: Boolean = false,
    val items: List<SearchItem> = emptyList(),
    val error: String? = null,
    /** true en cuanto hay una búsqueda hecha (para no mostrar "escribe algo" al volver). */
    val searched: Boolean = false
) {
    val isEmptyResult: Boolean
        get() = searched && !loading && error == null && items.isEmpty()
}
