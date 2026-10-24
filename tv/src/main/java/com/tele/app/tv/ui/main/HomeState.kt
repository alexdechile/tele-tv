package com.tele.app.tv.ui.main

import com.tele.app.data.addon.model.HomeRow

/** Estado del home. */
sealed interface HomeState {
    data object Loading : HomeState
    data class Error(val message: String) : HomeState
    data class Content(val rows: List<HomeRow>) : HomeState
}
