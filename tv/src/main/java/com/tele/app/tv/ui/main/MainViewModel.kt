package com.tele.app.tv.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.app.data.addon.AddonRepository
import com.tele.app.tv.ui.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Carga las filas del home consultando los addons configurados. */
class MainViewModel : ViewModel() {

    private val addons: AddonRepository = AppGraph.addons

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = HomeState.Loading
            _state.value = try {
                val rows = addons.homeRows()
                if (rows.isEmpty()) {
                    HomeState.Error("No se recibió contenido de ningún addon. Revisa la conexión.")
                } else {
                    HomeState.Content(rows)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                HomeState.Error(error.message ?: "Error al cargar el catálogo")
            }
        }
    }
}
