package com.tele.app.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

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

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLoading) {
            Text(text = "Preparando stream de torrent...")
        } else if (error != null) {
            Text(text = "Error: $error")
        } else {
            Text(text = "Reproductor de video (placeholder)")
        }
    }
}
