package com.tele.app.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.app.data.addon.AddonRepository
import com.tele.app.data.addon.model.SearchFilters
import com.tele.app.data.addon.model.SortOrder
import com.tele.app.tv.ui.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Buscador avanzado.
 *
 * Dos etapas, a propósito:
 *
 *  1. **Catálogo**: texto, género y tipo viajan al addon; responde en una sola
 *     request y la pantalla muestra resultados enseguida.
 *  2. **Ficha**: valoración y temporadas no existen en un catálogo, así que se
 *     piden en lotes y la lista se va afinando en vivo. Filtrar por "8 o más"
 *     exige esto sí o sí; hacerlo de golpe dejaría la pantalla en negro varios
 *     segundos en el TV box.
 */
class SearchViewModel : ViewModel() {

    private val addons: AddonRepository = AppGraph.addons

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val genres = try {
                addons.genres()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                emptyList()
            }
            _state.update { it.copy(genres = genres) }
        }

        // Sin filtros el catálogo devuelve lo popular: mejor abrir con contenido
        // que con una pantalla en blanco esperando que alguien elija un género.
        scheduleSearch(immediate = true)
    }

    /** Cambia los filtros y vuelve a buscar (con debounce, para no golpear en cada tecla). */
    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _state.update { it.copy(filters = transform(it.filters)) }
        scheduleSearch()
    }

    /** Fuerza una búsqueda inmediata (botón "Buscar"). */
    fun searchNow() = scheduleSearch(immediate = true)

    fun clearFilters() {
        _state.update { it.copy(filters = SearchFilters()) }
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()

        val filters = _state.value.filters

        searchJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)

            _state.update {
                it.copy(loading = true, enriching = false, error = null, searched = true, items = emptyList())
            }

            val candidates = try {
                addons.search(filters)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    it.copy(loading = false, error = error.message ?: "Error al buscar")
                }
                return@launch
            }

            val visible = candidates.take(MAX_RESULTS).map { SearchItem(it) }
            _state.update { it.copy(loading = false, items = visible, enriching = true) }

            if (visible.isEmpty()) {
                _state.update { it.copy(enriching = false) }
                return@launch
            }

            // Segunda etapa: la ficha de cada título, en lotes.
            val enriched = visible.toMutableList()
            for (start in visible.indices step DETAIL_BATCH) {
                val batch = visible.subList(start, minOf(start + DETAIL_BATCH, visible.size))

                val details = coroutineScope {
                    batch.map { item ->
                        async {
                            try {
                                addons.detail(item.meta)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                }

                batch.forEachIndexed { offset, item ->
                    val detail = details[offset]
                    enriched[start + offset] = item.copy(
                        rating = detail?.rating,
                        seasonCount = detail?.seasonCount
                    )
                }

                val view = applyFilters(enriched, filters)
                _state.update { it.copy(items = view) }
            }

            _state.update { it.copy(enriching = false) }
        }
    }

    /**
     * Filtros que el protocolo no puede hacer y por eso se aplican acá:
     * nota mínima, temporadas mínimas y orden por nota.
     */
    private fun applyFilters(items: List<SearchItem>, filters: SearchFilters): List<SearchItem> {
        val filtered = items.filter { item ->
            val ratingOk = filters.minRating?.let { minimum -> (item.rating ?: 0.0) >= minimum } ?: true

            val seasonsOk = when (val minimum = filters.minSeasons) {
                null -> true
                else -> item.meta.isSeries && (item.seasonCount ?: 0) >= minimum
            }

            ratingOk && seasonsOk
        }

        return when (filters.sort) {
            SortOrder.RATED -> filtered.sortedByDescending { it.rating ?: 0.0 }
            else -> filtered
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
        const val MAX_RESULTS = 42
        const val DETAIL_BATCH = 8
    }
}
