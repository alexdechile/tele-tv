package com.tele.app.tv.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.tele.app.tv.ui.navigation.PlaybackRequest

/**
 * Reproductor. ExoPlayer nunca habla con el torrent: recibe la URL HTTP local
 * que expone el motor (Milestone 2) y la reproduce como si fuera un video más.
 *
 * La superficie de video usa [PlayerView] dentro de [AndroidView] porque
 * media3-ui-compose no existe; es el puente oficial recomendado.
 */
@Composable
fun PlayerScreen(
    request: PlaybackRequest?,
    modifier: Modifier = Modifier,
    viewModel: TorrentStreamViewModel = remember { TorrentStreamViewModel() }
) {
    val context = LocalContext.current
    var playerError by remember { mutableStateOf<String?>(null) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playerError = error.errorCodeName +
                        (error.message?.let { ": $it" } ?: "")
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            viewModel.stop()
        }
    }

    val streamUrl by viewModel.streamUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(request?.magnetUri, request?.fileIndex) {
        val playback = request ?: return@LaunchedEffect
        viewModel.start(playback.magnetUri, playback.fileIndex)
    }

    LaunchedEffect(streamUrl) {
        val url = streamUrl ?: return@LaunchedEffect
        playerError = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val status = playerError
            ?: error
            ?: if (isLoading) "Preparando stream…" else streamUrl?.let { "Fuente: $it" }

        status?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            )
        }
    }
}
