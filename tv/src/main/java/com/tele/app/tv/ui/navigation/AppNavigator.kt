package com.tele.app.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tele.app.data.addon.TorrentLinks
import com.tele.app.data.addon.model.StreamSource
import com.tele.app.tv.ui.detail.DetailScreen
import com.tele.app.tv.ui.main.MainScreen
import com.tele.app.tv.ui.main.MainViewModel
import com.tele.app.tv.ui.player.PlayerScreen

@Composable
fun AppNavigator() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIN
    ) {
        composable(ROUTE_MAIN) {
            val viewModel: MainViewModel = viewModel()
            val state by viewModel.state.collectAsState()

            MainScreen(
                state = state,
                onItemSelected = { meta ->
                    NavPayload.selectedMeta = meta
                    navController.navigate(ROUTE_DETAIL)
                },
                onRetry = { viewModel.refresh() }
            )
        }

        composable(ROUTE_DETAIL) {
            val meta = NavPayload.selectedMeta
            if (meta == null) {
                // Se llegó aquí sin selección (por ejemplo, tras recrear la Activity).
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                DetailScreen(
                    meta = meta,
                    onPlay = { stream ->
                        NavPayload.playback = stream.toPlaybackRequest(meta.name)
                        navController.navigate(ROUTE_PLAYER)
                    }
                )
            }
        }

        composable(ROUTE_PLAYER) {
            PlayerScreen(
                request = NavPayload.playback,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun StreamSource.toPlaybackRequest(title: String?): PlaybackRequest? {
    // Copias locales: `infoHash`/`url` vienen de otro modulo, y Kotlin no puede
    // hacer smart cast sobre properties publicas de un modulo distinto.
    val hash = infoHash
    val directUrl = url

    val magnet = when {
        !hash.isNullOrBlank() -> TorrentLinks.magnet(hash, fileName ?: title)
        !directUrl.isNullOrBlank() -> directUrl
        else -> null
    } ?: return null

    return PlaybackRequest(
        magnetUri = magnet,
        fileIndex = fileIndex,
        title = title
    )
}

private const val ROUTE_MAIN = "main"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_PLAYER = "player"
