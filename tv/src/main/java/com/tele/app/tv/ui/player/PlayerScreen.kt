package com.tele.app.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun PlayerScreen(
    magnetUri: String,
    fileIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TorrentStreamViewModel = remember { TorrentStreamViewModel() }
) {
    val streamUrl by viewModel.streamUrl.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

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

    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        if (isLoading) {
            androidx.compose.material3.Text(
                text = "Preparando stream de torrent...",
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (error != null) {
            androidx.compose.material3.Text(
                text = "Error: $error",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            androidx.compose.material3.Text(
                text = "Reproductor de video (placeholder)",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
