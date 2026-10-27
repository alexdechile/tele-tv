package com.tele.app.tv.ui.player

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamState
import com.tele.app.tv.R
import com.tele.app.tv.audio.AudioAwareRenderersFactory
import com.tele.app.tv.audio.AudioSupportReport
import com.tele.app.tv.audio.readAudioSupport
import com.tele.app.tv.ui.navigation.PlaybackRequest
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private const val AUDIO_LOG_TAG = "TeleTvAudio"

/** Cuánto salta el seek al apretar izquierda/derecha en el control remoto. */
private const val SEEK_STEP_MS = 10_000L

// Paleta de la barra: píldoras claras, texto oscuro, foco ámbar.
// El video queda detrás del degradado, así que el contraste se mantiene sin
// importar qué escena esté en pantalla.
private val PillIdle = Color(0xF2FFFFFF)
private val PillPrimary = Color(0xFFFFFFFF)
private val PillIdleText = Color(0xFF141414)
private val PillFocus = Color(0xFFFFC24B)
private val PillFocusText = Color(0xFF1C1300)
private val PillFocusRing = Color(0xFFFFE1A3)
private val SeekAccent = Color(0xFFFFC24B)
private val StatusText = Color(0xFFEDEDED)
private val StatusError = Color(0xFFFF8A80)
private val StatusSecondary = Color(0xFF9E9E9E)

enum class SubtitleChoice(val label: String, val langCode: String?) {
    SPANISH("Español", "spa"),
    ENGLISH("Inglés", "eng"),
    OFF("Desactivados", null)
}

private fun SubtitleChoice.next(): SubtitleChoice = when (this) {
    SubtitleChoice.SPANISH -> SubtitleChoice.ENGLISH
    SubtitleChoice.ENGLISH -> SubtitleChoice.OFF
    SubtitleChoice.OFF -> SubtitleChoice.SPANISH
}

/**
 * Reproductor. ExoPlayer nunca habla con el torrent: recibe la URL HTTP local
 * que expone el motor y la reproduce como si fuera un video más.
 *
 * La superficie de video usa [TouchPassThroughPlayerView] dentro de [AndroidView]
 * (media3-ui-compose no existe). Es crítico que esa vista **no** se quede con
 * los toques, o la barra de controles de Compose se dibuja pero no recibe nada.
 *
 * El audio se configura con las capacidades reales del equipo, para que un TV
 * box con AC3/DTS por HDMI use bitstream en vez de quedarse en silencio.
 */
@Composable
fun PlayerScreen(
    request: PlaybackRequest?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

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

    var subtitleChoice by remember { mutableStateOf(SubtitleChoice.SPANISH) }
    var isFullScreenZoom by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Estado del seek. ExoPlayer no expone la posición como estado de Compose,
    // así que la refrescamos con un reloj propio.
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }

    val playFocus = remember { FocusRequester() }
    val closedFocus = remember { FocusRequester() }

    LaunchedEffect(audioReport) { audioReport.log(AUDIO_LOG_TAG) }

    val player = remember(audioCapabilities) {
        val renderersFactory = AudioAwareRenderersFactory(context, audioCapabilities)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(120_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                50_000,
                3_000,
                5_000
            )
            .build()

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        playerError = error.errorCodeName +
                            (error.message?.let { ": $it" } ?: "")
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
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
            player.stop()
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

    // Reloj del seek: 4 lecturas por segundo no se nota y no cuesta nada.
    LaunchedEffect(player) {
        while (true) {
            durationMs = player.duration.coerceAtLeast(0L)
            positionMs = player.currentPosition.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    // Configurar idioma o desactivar subtítulos
    LaunchedEffect(subtitleChoice, player) {
        val p = player
        if (subtitleChoice.langCode == null) {
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(subtitleChoice.langCode)
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
    }

    // Ajuste de pantalla completa (Ajustar vs Llenar)
    LaunchedEffect(isFullScreenZoom, playerViewRef) {
        playerViewRef?.resizeMode = if (isFullScreenZoom) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    val seekTo: (Long) -> Unit = { target ->
        val total = durationMs
        if (total > 0L) {
            val clamped = target.coerceIn(0L, total)
            player.seekTo(clamped)
            // Refleja el salto al instante, sin esperar al siguiente tick.
            positionMs = clamped
        }
    }

    val statusLine = playerError
        ?: if (isBuffering && state is TorrentStreamState.Ready) {
            "Cargando el video…"
        } else {
            statusText(state, readyInfo?.fileName)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Ojo: acá NO va `clickable`. Un `clickable` es enfocable, y ese
            // foco fantasma se robaba la navegación del control remoto: el dpad
            // no llegaba a los botones y OK cerraba la barra. Con puntero puro
            // el fondo alterna controles y el foco se queda donde corresponde.
            .pointerInput(Unit) {
                detectTapGestures { controlsOpen = !controlsOpen }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.tele_player_view, null) as TouchPassThroughPlayerView
                view.apply {
                    keepScreenOn = true
                    this.player = player
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón discreto para recuperar los controles cuando están ocultos.
        if (!controlsOpen) {
            ControlPill(
                text = "▲ Controles",
                onClick = { controlsOpen = true },
                focusRequester = closedFocus,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(28.dp)
            )
        }

        AnimatedVisibility(
            visible = controlsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ControlBar(
                playFocusRequester = playFocus,
                onBack = onBack,
                isPlaying = isPlaying,
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                isZoom = isFullScreenZoom,
                onToggleZoom = { isFullScreenZoom = !isFullScreenZoom },
                subtitle = subtitleChoice,
                onCycleSubtitle = { subtitleChoice = subtitleChoice.next() },
                onHide = { controlsOpen = false },
                statusLine = statusLine,
                statusIsError = playerError != null,
                audioLine = audioText(audioMimeType, audioTrackSupported, audioReport),
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = seekTo
            )
        }
    }
}

/**
 * Barra inferior: estado arriba, seek al medio, botones abajo.
 *
 * El fondo es un degradado (transparente → negro), no una banda de color plano:
 * así el video sigue viéndose detrás y el texto nunca pierde contraste.
 */
@Composable
private fun ControlBar(
    playFocusRequester: FocusRequester,
    onBack: () -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    isZoom: Boolean,
    onToggleZoom: () -> Unit,
    subtitle: SubtitleChoice,
    onCycleSubtitle: () -> Unit,
    onHide: () -> Unit,
    statusLine: String,
    statusIsError: Boolean,
    audioLine: String,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit
) {
    // Con control remoto tiene que haber algo enfocado desde el primer frame,
    // si no la tecla OK no hace nada. El nodo puede no estar adjunto todavía,
    // por eso reintentamos un par de frames.
    LaunchedEffect(Unit) {
        repeat(3) {
            if (runCatching { playFocusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(50)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x00000000), Color(0xB3060606), Color(0xF5060606))
                )
            )
            // Come el toque sobre el fondo del footer: apuntar a un botón y
            // errarle por 10px no debería cerrar toda la barra.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) StatusError else StatusText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = audioLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusSecondary,
                    maxLines = 1
                )
            }

            SeekRow(
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = onSeek,
                onCenterKey = onTogglePlay
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlPill(text = "← Volver", onClick = onBack)

                ControlPill(
                    text = if (isPlaying) "Pausa" else "▶ Reproducir",
                    onClick = onTogglePlay,
                    primary = true,
                    focusRequester = playFocusRequester
                )

                ControlPill(
                    text = if (isZoom) "Pantalla: Llenar" else "Pantalla: Ajustar",
                    onClick = onToggleZoom
                )

                ControlPill(
                    text = "Subtítulos: ${subtitle.label}",
                    onClick = onCycleSubtitle
                )

                Spacer(modifier = Modifier.weight(1f))

                ControlPill(text = "× Ocultar", onClick = onHide)
            }
        }
    }
}

/**
 * Barra de progreso navegable.
 *
 * - Mouse: clic en cualquier punto salta ahí; arrastrar hace scrubbing y recién
 *   busca al soltar (buscar en cada píxel sobre un torrent es una masacre).
 * - Control remoto: izquierda/derecha ±10s, OK pausa/reproduce.
 *
 * El gris tenue detrás es el buffer ya descargado por ExoPlayer.
 */
@Composable
private fun SeekRow(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    onCenterKey: () -> Unit
) {
    val enabled = durationMs > 0L
    var isFocused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val trackHeight by animateDpAsState(
        targetValue = if (isFocused) 14.dp else 6.dp,
        label = "seekTrackHeight"
    )

    val progressFraction = if (enabled) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = if (enabled) {
        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    val shownFraction = if (dragging) dragFraction else progressFraction
    val shownPosition = if (dragging) (dragFraction * durationMs).toLong() else positionMs

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = formatTime(shownPosition),
            style = MaterialTheme.typography.labelMedium,
            color = StatusText,
            maxLines = 1
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable(enabled = enabled)
                .onKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeek(positionMs - SEEK_STEP_MS)
                            true
                        }

                        Key.DirectionRight -> {
                            onSeek(positionMs + SEEK_STEP_MS)
                            true
                        }

                        Key.DirectionCenter, Key.Enter -> {
                            onCenterKey()
                            true
                        }

                        else -> false
                    }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((fraction * durationMs).toLong())
                    }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek((dragFraction * durationMs).toLong())
                        },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                val barHeight = trackHeight.toPx()
                val centerY = size.height / 2f
                val top = centerY - barHeight / 2f
                val corner = CornerRadius(barHeight / 2f, barHeight / 2f)

                drawRoundRect(
                    color = if (enabled) Color(0x4DFFFFFF) else Color(0x26FFFFFF),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, barHeight),
                    cornerRadius = corner
                )

                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = Color(0x80FFFFFF),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * bufferedFraction, barHeight),
                        cornerRadius = corner
                    )
                }

                if (shownFraction > 0f) {
                    drawRoundRect(
                        color = if (isFocused) SeekAccent else Color(0xFFFFFFFF),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * shownFraction, barHeight),
                        cornerRadius = corner
                    )
                }

                if (enabled && (isFocused || dragging)) {
                    val center = Offset(size.width * shownFraction, centerY)
                    drawCircle(color = SeekAccent, radius = 11.dp.toPx(), center = center)
                    drawCircle(
                        color = Color(0xFF1C1300),
                        radius = 4.dp.toPx(),
                        center = center
                    )
                }
            }
        }

        Text(
            text = if (enabled) formatTime(durationMs) else "--:--",
            style = MaterialTheme.typography.labelMedium,
            color = StatusSecondary,
            maxLines = 1
        )
    }
}

/**
 * Píldora de acción. Clara por defecto (fondo blanco, texto casi negro) para
 * que tenga contraste real sobre cualquier video; al enfocarse pasa a ámbar con
 * un anillo claro, que es la señal de foco de 10-foot UI.
 */
@Composable
private fun ControlPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "pillScale"
    )

    val shape = RoundedCornerShape(percent = 50)
    val containerColor = when {
        isFocused -> PillFocus
        primary -> PillPrimary
        else -> PillIdle
    }
    val contentColor = if (isFocused) PillFocusText else PillIdleText

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 8.dp, shape = shape)
            .background(color = containerColor, shape = shape)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) PillFocusRing else Color(0x14000000),
                shape = shape
            )
            .then(focusModifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (primary) 26.dp else 18.dp,
                vertical = 13.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
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

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatRate(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> "0 KB/s"
    bytesPerSecond >= 1024 * 1024 ->
        String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))

    else -> String.format(Locale.US, "%d KB/s", bytesPerSecond / 1024)
}
