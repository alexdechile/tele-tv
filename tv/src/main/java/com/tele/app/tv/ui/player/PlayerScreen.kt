package com.tele.app.tv.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.focus.focusRestorer
import androidx.tv.material3.focus.focusRequester
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlayerScreen(
    magnetUri: String,
    fileIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: TorrentStreamViewModel = hiltViewModel()

    var streamUrl by viewModel.streamUrl.collectAsStateWithLifecycle()
    var isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var error by viewModel.error.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    LaunchedEffect(magnetUri, fileIndex) {
        viewModel.start(magnetUri, fileIndex)
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl != null) {
            val mediaItem = MediaItem.fromUri(streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .focusRequester { /* requester -> requestFocus() */ }
            .focusRestorer(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoading) {
            Text(
                text = "Preparando stream de torrent...",
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (error != null) {
            Text(
                text = "Error: $error",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // TODO: Reemplazar por Video(player = player) cuando se añada
            //       androidx.media3:media3-ui-compose o equivalente.
            Text(
                text = "Reproductor de video (placeholder)",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
