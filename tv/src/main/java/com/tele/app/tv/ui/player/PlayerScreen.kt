package com.tele.app.tv.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamState
import com.tele.app.tv.audio.AudioAwareRenderersFactory
import com.tele.app.tv.audio.AudioSupportReport
import com.tele.app.tv.audio.readAudioSupport
import com.tele.app.tv.ui.navigation.PlaybackRequest
import java.io.File
import java.util.Locale

private const val AUDIO_LOG_TAG = "TeleTvAudio"

/**
 * Reproductor. ExoPlayer nunca habla con el torrent: recibe la URL HTTP local
 * que expone el motor y la reproduce como si fuera un video más.
 *
 * La superficie de video usa [PlayerView] dentro de [AndroidView] porque
 * media3-ui-compose no existe; es el puente oficial recomendado.
 *
 * El audio se configura con las capacidades reales del equipo, para que un TV
 * box con AC3/DTS por HDMI use bitstream en vez de quedarse en silencio.
 */
@Composable
fun PlayerScreen(
    request: PlaybackRequest?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember(context) {
        TorrentEngine(File(context.cacheDir, "torrents"))
    }
    val viewModel = remember(engine) { TorrentStreamViewModel(engine) }

    val audioReport = remember(context) { readAudioSupport(context) }
    val audioCapabilities = remember(context) {
        // Si el ROM falla al leer las capacidades, caemos a PCM (sin passthrough)
        // antes que reventar el reproductor.
        runCatching { AudioCapabilities.getCapabilities(context) }
            .getOrDefault(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
    }

    var playerError by remember { mutableStateOf<String?>(null) }
    var audioMimeType by remember { mutableStateOf<String?>(null) }
    var audioTrackSupported by remember { mutableStateOf(true) }

    LaunchedEffect(audioReport) { audioReport.log(AUDIO_LOG_TAG) }

    val player = remember(audioCapabilities) {
        val renderersFactory = AudioAwareRenderersFactory(context, audioCapabilities)
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playerError = error.errorCodeName +
                            (error.message?.let { ": $it" } ?: "")
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val audioGroup = tracks.groups
                            .firstOrNull { group -> group.type == C.TRACK_TYPE_AUDIO }
                        if (audioGroup == null) {
                            audioMimeType = null
                            audioTrackSupported = true
                            return
                        }
                        val selectedIndex = (0 until audioGroup.length)
                            .firstOrNull { index -> audioGroup.isTrackSelected(index) }
                            ?: 0
                        audioMimeType = audioGroup.getTrackFormat(selectedIndex).sampleMimeType
                        audioTrackSupported = audioGroup.isSupported()
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

    val state by viewModel.state.collectAsState()
    val readyInfo = (state as? TorrentStreamState.Ready)?.info

    LaunchedEffect(request?.magnetUri, request?.fileIndex) {
        val playback = request ?: return@LaunchedEffect
        viewModel.start(playback.magnetUri, playback.fileIndex)
    }

    LaunchedEffect(readyInfo?.url) {
        val url = readyInfo?.url ?: return@LaunchedEffect
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playerError ?: statusText(state, readyInfo?.fileName),
                color = Color.White
            )
            Text(
                text = audioText(audioMimeType, audioTrackSupported, audioReport),
                color = Color.White
            )
        }
    }
}

private fun statusText(state: TorrentStreamState, fileName: String?): String = when (state) {
    TorrentStreamState.Resolving -> "Obteniendo metadatos del torrent…"

    is TorrentStreamState.Ready -> "Reproduciendo ${fileName ?: "video"}"

    is TorrentStreamState.Progress -> buildString {
        append("Descargando ${state.percent.toInt()}% · ${formatRate(state.downloadRate)}")
        append(" · ${state.peers} peers")
        if (state.state == "SEEDING" || state.state == "FINISHED") append(" · completo")
    }

    is TorrentStreamState.Error -> "Error: ${state.message}"
}

/**
 * Dice, en una línea, qué audio trae la fuente y por qué camino lo puede sacar
 * este equipo. Si no hay camino posible lo avisa explícitamente para no dejar al
 * usuario pensando que el torrent falló.
 */
private fun audioText(
    mimeType: String?,
    trackSupported: Boolean,
    report: AudioSupportReport
): String {
    val mime = mimeType ?: return "Audio: esperando pista…"
    val support = report.byMimeType(mime)

    val route = when {
        support == null -> "sin datos del equipo"
        support.passthrough -> "passthrough HDMI"
        support.hasDecoder -> "decoder del equipo"
        support.offload -> "offload DSP"
        else -> "SIN SOPORTE"
    }

    val label = support?.name ?: mime.removePrefix("audio/")
    val warning = if (!trackSupported || support?.isUsable == false) {
        " · ⚠ elige otra fuente"
    } else {
        ""
    }

    return "Audio: $label ($route)$warning"
}

private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> "0 KB/s"
    bytesPerSecond >= 1024 * 1024 ->
        String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))

    else -> String.format(Locale.US, "%d KB/s", bytesPerSecond / 1024)
}
