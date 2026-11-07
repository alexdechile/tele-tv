package com.tele.app.tv.ui.player

import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.tele.app.data.addon.model.SubtitleSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tele.app.core.torrent.TorrentEngine
import com.tele.app.core.torrent.TorrentStreamInfo
import com.tele.app.core.torrent.TorrentStreamState
import com.tele.app.tv.R
import com.tele.app.tv.audio.AudioAwareRenderersFactory
import com.tele.app.tv.audio.AudioSupportReport
import com.tele.app.tv.audio.readAudioSupport
import com.tele.app.tv.ui.components.TeleCard
import com.tele.app.tv.ui.navigation.PlaybackRequest
import com.tele.app.tv.ui.theme.PastelTheme
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import android.content.Context
import androidx.compose.ui.unit.Dp

private const val AUDIO_LOG_TAG = "TeleTvAudio"
/** Borrado del material bajado (se ve el video y se va de la TV). */
private const val CACHE_LOG_TAG = "TeleTvCache"
/** Estado del motor mientras resuelve metadatos / descarga. */
private const val TORRENT_LOG_TAG = "TeleTvTorrent"
/** Cuánto salta el seek al apretar izquierda/derecha en el control remoto. */
private const val SEEK_STEP_MS = 10_000L

// Paleta de la barra: tonos pasteles suaves y descansados para la vista.
// El video queda detrás del degradado, así que el contraste se mantiene sin
// importar qué escena esté en pantalla, transmitiendo tranquilidad.
private val PillIdle = Color(0xEEF3F0FF)
private val PillPrimary = PastelTheme.LavenderLight
private val PillIdleText = Color(0xFF131722)
private val PillFocus = PastelTheme.Lavender
private val PillFocusText = PastelTheme.LavenderDark
private val PillFocusRing = PastelTheme.LavenderLight
private val SeekAccent = PastelTheme.Lavender
private val StatusText = PastelTheme.TextPrimary
private val StatusError = PastelTheme.Coral
private val StatusSecondary = PastelTheme.TextMuted

data class SubtitleTrackItem(
    val id: String,
    val label: String,
    val language: String?,
    val isForced: Boolean,
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int
)

data class SubtitleOption(
    val id: String,
    val label: String,
    val track: SubtitleTrackItem?
)

enum class SubtitleBackgroundPreference(val id: String, val label: String) {
    SOLID("solid", "Fondo negro sólido (cubre subs)"),
    TRANSLUCENT("translucent", "Fondo translúcido"),
    NONE("none", "Sin fondo (solo contorno)")
}

enum class SubtitleSizePreference(val id: String, val label: String, val fraction: Float) {
    NORMAL("normal", "Letra normal", 0.0533f),
    LARGE("large", "Letra grande", 0.068f),
    EXTRA_LARGE("xlarge", "Letra muy grande", 0.082f)
}

/** Una pista de audio del contenedor, tal como la reporta ExoPlayer. */
data class AudioTrackItem(
    val id: String,
    val label: String,
    val mediaTrackGroup: TrackGroup,
    val trackIndex: Int
)

private fun buildSubtitleLabel(format: Format, trackNumber: Int): String = TrackLabels.subtitle(
    language = format.language,
    customLabel = format.label,
    isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
    isSdh = format.label?.contains("sdh", ignoreCase = true) == true ||
        (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0,
    trackNumber = trackNumber
)

private fun buildAudioLabel(format: Format, trackNumber: Int): String = TrackLabels.audio(
    language = format.language,
    mimeType = format.sampleMimeType,
    channelCount = format.channelCount,
    trackNumber = trackNumber
)

/** Etiqueta corta para el botón de la barra. */
private fun shortAudioLabel(items: List<AudioTrackItem>, selectedId: String?): String =
    TrackLabels.shortAudio(
        labels = items.map { it.label },
        selectedIndex = items.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
    )

private fun applyAudioSelection(player: ExoPlayer, item: AudioTrackItem) {
    val builder = player.trackSelectionParameters.buildUpon()
    builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
    builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
    builder.setOverrideForType(
        TrackSelectionOverride(item.mediaTrackGroup, listOf(item.trackIndex))
    )
    player.trackSelectionParameters = builder.build()
}

/**
 * Arma el `MediaItem` con los subtítulos sueltos del torrent y los remotos (OpenSubtitles).
 *
 * Van como `SubtitleConfiguration`: los locales apuntando al servidor HTTP local y
 * los remotos directo a la CDN de Stremio. Media3 los parsea según el `mimeType`.
 */
private fun buildMediaItem(
    info: TorrentStreamInfo,
    remoteSubtitles: List<SubtitleSource> = emptyList()
): MediaItem {
    val localSubtitles = info.subtitles.map { subtitle ->
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setMimeType(subtitle.contentType)
            .setLabel(subtitle.label)
            .apply { subtitle.language?.let { setLanguage(it) } }
            .setSelectionFlags(0)
            .build()
    }

    val onlineSubtitles = remoteSubtitles.map { sub ->
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
            .setMimeType("application/x-subrip")
            .setLabel(sub.label)
            .setLanguage(sub.lang)
            .setSelectionFlags(0)
            .build()
    }

    return MediaItem.Builder()
        .setUri(info.url)
        .setSubtitleConfigurations(localSubtitles + onlineSubtitles)
        .build()
}

private fun applySubtitleSelection(player: ExoPlayer, option: SubtitleOption) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option.track == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        builder.setOverrideForType(
            TrackSelectionOverride(
                option.track.mediaTrackGroup,
                listOf(option.track.trackIndex)
            )
        )
    }
    player.trackSelectionParameters = builder.build()
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
    var controlsOpen by remember { mutableStateOf(true) }
    var trackMenuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showDeleteDialog -> showDeleteDialog = false
            trackMenuOpen -> trackMenuOpen = false
            controlsOpen -> controlsOpen = false
            else -> onBack()
        }
    }

    val context = LocalContext.current
    val engine = remember(context) {
        TorrentEngine(File(context.cacheDir, "torrents")).apply {
            // El material se borra al salir del reproductor (a menos que se marque conservar).
            // Si el borrado no se puede completar, queda rastro en logcat y la app lo
            // vuelve a intentar en el siguiente arranque.
            onCleanupProblem = { message -> Log.w(CACHE_LOG_TAG, message) }
            onDiagnostic = { message -> Log.d(TORRENT_LOG_TAG, message) }
        }
    }
    val viewModel = remember(engine) { TorrentStreamViewModel(engine) }

    val isKept by viewModel.isKept.collectAsState()
    val actionNotification by viewModel.actionNotification.collectAsState()

    // Oculta automáticamente el aviso de conservar/eliminar a los 3.5 segundos
    LaunchedEffect(actionNotification) {
        if (actionNotification != null) {
            delay(3500)
            viewModel.clearNotification()
        }
    }

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

    var availableSubtitles by remember {
        mutableStateOf(listOf(SubtitleOption("off", "Desactivados", null)))
    }
    var selectedSubtitle by remember {
        mutableStateOf(SubtitleOption("off", "Desactivados", null))
    }
    var userChangedSubtitle by remember { mutableStateOf(false) }
    var availableAudio by remember { mutableStateOf(listOf<AudioTrackItem>()) }
    var selectedAudioId by remember { mutableStateOf<String?>(null) }
    var isFullScreenZoom by remember { mutableStateOf(false) }
    var menuStartsWithAudio by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val prefs = remember(context) { context.getSharedPreferences("tele_subtitles_pref", Context.MODE_PRIVATE) }
    var subBgPref by remember {
        val saved = prefs.getString("sub_bg", SubtitleBackgroundPreference.SOLID.id)
        mutableStateOf(SubtitleBackgroundPreference.entries.firstOrNull { it.id == saved } ?: SubtitleBackgroundPreference.SOLID)
    }
    var subSizePref by remember {
        val saved = prefs.getString("sub_size", SubtitleSizePreference.NORMAL.id)
        mutableStateOf(SubtitleSizePreference.entries.firstOrNull { it.id == saved } ?: SubtitleSizePreference.NORMAL)
    }

    // Configuración estética de subtítulos en ExoPlayer (fondo sólido para tapar hardcoded y tamaño de fuente)
    LaunchedEffect(playerViewRef, subBgPref, subSizePref) {
        val view = playerViewRef?.subtitleView ?: return@LaunchedEffect
        view.setApplyEmbeddedStyles(false)
        val bgColor = when (subBgPref) {
            SubtitleBackgroundPreference.SOLID -> android.graphics.Color.BLACK
            SubtitleBackgroundPreference.TRANSLUCENT -> android.graphics.Color.argb(200, 0, 0, 0)
            SubtitleBackgroundPreference.NONE -> android.graphics.Color.TRANSPARENT
        }
        val edgeType = if (subBgPref == SubtitleBackgroundPreference.NONE) {
            CaptionStyleCompat.EDGE_TYPE_OUTLINE
        } else {
            CaptionStyleCompat.EDGE_TYPE_NONE
        }
        val style = CaptionStyleCompat(
            android.graphics.Color.WHITE,
            bgColor,
            android.graphics.Color.TRANSPARENT,
            edgeType,
            android.graphics.Color.BLACK,
            null
        )
        view.setStyle(style)
        view.setFractionalTextSize(subSizePref.fraction)
    }

    // Estado del seek. ExoPlayer no expone la posición como estado de Compose,
    // así que la refrescamos con un reloj propio.
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var bufferedMs by remember { mutableStateOf(0L) }

    val playFocus = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val notifyInteraction = { lastInteractionTime = System.currentTimeMillis() }

    // Auto-ocultar controles tras 5 segundos de inactividad mientras se reproduce
    // el video. Con el menú de pistas abierto no se oculta: el menú se quedaría
    // flotando solo sobre la película, sin la barra de la que salió.
    LaunchedEffect(controlsOpen, isPlaying, lastInteractionTime, trackMenuOpen) {
        if (controlsOpen && isPlaying && !trackMenuOpen) {
            delay(5000)
            controlsOpen = false
        }
    }

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
                        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        val audioOptions = mutableListOf<AudioTrackItem>()
                        var audioCounter = 1
                        for (group in audioGroups) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                audioOptions.add(
                                    AudioTrackItem(
                                        id = "${group.mediaTrackGroup.id}_$i",
                                        label = buildAudioLabel(format, audioCounter++),
                                        mediaTrackGroup = group.mediaTrackGroup,
                                        trackIndex = i
                                    )
                                )
                            }
                        }
                        availableAudio = audioOptions
                        selectedAudioId = audioOptions.firstOrNull { item ->
                            val group = audioGroups.firstOrNull {
                                it.mediaTrackGroup.id == item.mediaTrackGroup.id
                            } ?: return@firstOrNull false
                            group.isTrackSelected(item.trackIndex)
                        }?.id ?: audioOptions.firstOrNull()?.id

                        val audioGroup = audioGroups.firstOrNull()
                        if (audioGroup == null) {
                            audioMimeType = null
                            audioTrackSupported = true
                        } else {
                            val selectedIndex = (0 until audioGroup.length)
                                .firstOrNull { index -> audioGroup.isTrackSelected(index) }
                                ?: 0
                            audioMimeType = audioGroup.getTrackFormat(selectedIndex).sampleMimeType
                            audioTrackSupported = audioGroup.isSupported()
                        }

                        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        val options = mutableListOf<SubtitleOption>()
                        options.add(SubtitleOption("off", "Desactivados", null))

                        var counter = 1
                        for (group in textGroups) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
                                val lang = format.language?.lowercase()?.trim()
                                val label = buildSubtitleLabel(format, counter++)
                                val item = SubtitleTrackItem(
                                    id = "${group.mediaTrackGroup.id}_$i",
                                    label = label,
                                    language = lang,
                                    isForced = isForced,
                                    mediaTrackGroup = group.mediaTrackGroup,
                                    trackIndex = i
                                )
                                options.add(SubtitleOption(item.id, item.label, item))
                            }
                        }
                        availableSubtitles = options

                        if (!userChangedSubtitle) {
                            val defaultSpanish = options.firstOrNull { opt ->
                                val t = opt.track ?: return@firstOrNull false
                                val lang = t.language ?: ""
                                val isSpanish = lang in listOf("es", "spa", "es-419", "es-es", "es-la") ||
                                    t.label.contains("español", ignoreCase = true) ||
                                    t.label.contains("spanish", ignoreCase = true) ||
                                    t.label.contains("castellano", ignoreCase = true)
                                isSpanish && !t.isForced
                            } ?: options.firstOrNull { opt ->
                                val t = opt.track ?: return@firstOrNull false
                                val lang = t.language ?: ""
                                lang in listOf("es", "spa", "es-419", "es-es", "es-la") ||
                                    t.label.contains("español", ignoreCase = true) ||
                                    t.label.contains("spanish", ignoreCase = true) ||
                                    t.label.contains("castellano", ignoreCase = true)
                            }

                            if (defaultSpanish != null) {
                                selectedSubtitle = defaultSpanish
                                applySubtitleSelection(this@apply, defaultSpanish)
                            } else {
                                selectedSubtitle = options.first()
                                applySubtitleSelection(this@apply, options.first())
                            }
                        }
                    }
                })
            }
    }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
            viewModel.stop()
            // Cierra la sesión nativa de libtorrent. Sin esto quedaba viva una
            // sesión con su socket de escucha y sus trackers después de salir
            // del video: la app seguía "presente" en la red sin mostrar nada.
            engine.shutdown()
        }
    }

    val state by viewModel.state.collectAsState()
    // La URL vive aparte del estado de progreso: si se leyera de `state`, el
    // primer `Progress` la pisaría y ExoPlayer nunca llegaría a preparar el video.
    val readyInfo by viewModel.ready.collectAsState()

    LaunchedEffect(request?.magnetUri, request?.fileIndex) {
        val playback = request ?: return@LaunchedEffect
        viewModel.start(
            playback.magnetUri,
            playback.fileIndex,
            playback.mediaType,
            playback.mediaId
        )
    }

    LaunchedEffect(readyInfo?.url) {
        val info = readyInfo ?: return@LaunchedEffect
        playerError = null

        var onlineSubs = viewModel.remoteSubtitles.value
        if (onlineSubs.isEmpty() && request?.mediaId != null) {
            withTimeoutOrNull(1200) {
                viewModel.remoteSubtitles.first { it.isNotEmpty() }
            }?.let { onlineSubs = it }
        }

        player.setMediaItem(buildMediaItem(info, onlineSubs))
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

    // Ya no se ciclan las pistas a ciegas: se abre el menú y se elige viendo
    // la lista. Con 6 subtítulos en un release, "OK hasta que salga el español"
    // era peor que no tener la función.
    val openTrackMenu: (Boolean) -> Unit = { focusAudio ->
        menuStartsWithAudio = focusAudio
        trackMenuOpen = true
    }

    val subtitleButtonText = when {
        availableSubtitles.size <= 1 -> "Subtítulos: No disponibles"
        selectedSubtitle.track == null -> "Subtítulos: Desactivados"
        else -> "Subtítulos: ${selectedSubtitle.label}"
    }

    val handleKeyEvent: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        // Con el diálogo de borrado o el menú de pistas abierto, las teclas son de esos elementos.
        if (showDeleteDialog || trackMenuOpen) {
            false
        } else if (event.type == KeyEventType.KeyDown) {
            notifyInteraction()
            if (!controlsOpen) {
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                    Key.Menu, Key.X, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        controlsOpen = true
                        true
                    }
                    else -> false
                }
            } else {
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        controlsOpen = false
                        true
                    }
                    Key.X, Key.Menu -> {
                        controlsOpen = false
                        true
                    }
                    else -> false
                }
            }
        } else {
            false
        }
    }

    LaunchedEffect(controlsOpen) {
        if (controlsOpen) {
            repeat(4) {
                if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        } else {
            repeat(4) {
                if (runCatching { rootFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
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
        notifyInteraction()
        val total = durationMs
        if (total > 0L) {
            val clamped = target.coerceIn(0L, total)
            player.seekTo(clamped)
            // Refleja el salto al instante, sin esperar al siguiente tick.
            positionMs = clamped
        }
    }

    val statusLine = playerError ?: when {
        readyInfo == null -> statusText(state, null)
        isBuffering -> "Cargando el video…" + progressSuffix(state)
        isPlaying -> "Reproduciendo ${readyInfo?.fileName ?: "video"}" + progressSuffix(state)
        else -> "En pausa · ${readyInfo?.fileName ?: "video"}" + progressSuffix(state)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable(enabled = !controlsOpen)
            .onPreviewKeyEvent(handleKeyEvent)
            // Ojo: acá NO va `clickable`. Un `clickable` es enfocable, y ese
            // foco fantasma se robaba la navegación del control remoto: el dpad
            // no llegaba a los botones y OK cerraba la barra. Con puntero puro
            // el fondo alterna controles y el foco se queda donde corresponde.
            .pointerInput(Unit) {
                detectTapGestures {
                    notifyInteraction()
                    controlsOpen = !controlsOpen
                }
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

        // Cuando controlsOpen es false, la pantalla queda 100% limpia sin botones
        // obtrusivos flotando sobre la película. Con la tecla OK del control remoto
        // (o D-pad, Menú, etc.) vuelven a aparecer.

        // Notificación suave flotante (Conservar / Eliminar) con colores pasteles
        AnimatedVisibility(
            visible = actionNotification != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF013192B))
                    .border(1.5.dp, if (isKept) PastelTheme.Mint else PastelTheme.Coral, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = actionNotification ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isKept) PastelTheme.MintLight else PastelTheme.Coral
                )
            }
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
                onTogglePlay = {
                    notifyInteraction()
                    if (player.isPlaying) player.pause() else player.play()
                },
                isZoom = isFullScreenZoom,
                onToggleZoom = {
                    notifyInteraction()
                    isFullScreenZoom = !isFullScreenZoom
                },
                audioLabel = shortAudioLabel(availableAudio, selectedAudioId),
                onOpenAudioTracks = {
                    notifyInteraction()
                    openTrackMenu(true)
                },
                isKept = isKept,
                onToggleKeep = {
                    notifyInteraction()
                    viewModel.toggleKeep()
                },
                onRequestDelete = {
                    notifyInteraction()
                    showDeleteDialog = true
                },
                statusLine = statusLine,
                statusIsError = playerError != null,
                audioLine = audioText(audioMimeType, audioTrackSupported, audioReport),
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                onSeek = seekTo
            )
        }

        if (showDeleteDialog) {
            DeleteConfirmDialog(
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.deleteDownloadNow { onBack() }
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        AnimatedVisibility(
            visible = trackMenuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 168.dp)
        ) {
            TrackMenu(
                audioOptions = availableAudio,
                selectedAudioId = selectedAudioId,
                subtitleOptions = availableSubtitles,
                selectedSubtitleId = selectedSubtitle.id,
                focusAudio = menuStartsWithAudio,
                subBgPref = subBgPref,
                subSizePref = subSizePref,
                onSelectAudio = { item ->
                    notifyInteraction()
                    selectedAudioId = item.id
                    applyAudioSelection(player, item)
                    trackMenuOpen = false
                },
                onSelectSubtitle = { option ->
                    notifyInteraction()
                    userChangedSubtitle = true
                    selectedSubtitle = option
                    applySubtitleSelection(player, option)
                    trackMenuOpen = false
                },
                onSelectSubBg = { pref ->
                    notifyInteraction()
                    subBgPref = pref
                    prefs.edit().putString("sub_bg", pref.id).apply()
                },
                onSelectSubSize = { pref ->
                    notifyInteraction()
                    subSizePref = pref
                    prefs.edit().putString("sub_size", pref.id).apply()
                },
                onClose = { trackMenuOpen = false }
            )
        }
    }
}

/**
 * Menú de pistas: audio a la izquierda, subtítulos al centro, estilo/fondo a la derecha.
 *
 * Cada opción es una tarjeta enfocable; la elegida se marca con un punto, para
 * que se vea de un vistazo qué está sonando y qué subtítulo está activo sin
 * volver a la barra.
 */
@Composable
private fun TrackMenu(
    audioOptions: List<AudioTrackItem>,
    selectedAudioId: String?,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleId: String,
    focusAudio: Boolean,
    subBgPref: SubtitleBackgroundPreference,
    subSizePref: SubtitleSizePreference,
    onSelectAudio: (AudioTrackItem) -> Unit,
    onSelectSubtitle: (SubtitleOption) -> Unit,
    onSelectSubBg: (SubtitleBackgroundPreference) -> Unit,
    onSelectSubSize: (SubtitleSizePreference) -> Unit,
    onClose: () -> Unit
) {
    val firstAudioFocus = remember { FocusRequester() }
    val firstSubtitleFocus = remember { FocusRequester() }

    // El nodo puede no estar adjunto en el primer frame del fade-in.
    LaunchedEffect(focusAudio, audioOptions.size, subtitleOptions.size) {
        val target = when {
            focusAudio && audioOptions.isNotEmpty() -> firstAudioFocus
            !focusAudio -> firstSubtitleFocus
            audioOptions.isNotEmpty() -> firstAudioFocus
            else -> return@LaunchedEffect
        }
        repeat(4) {
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF0101726))
            .border(1.dp, PastelTheme.SurfaceBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TrackColumn(
                title = "Audio",
                emptyText = "Sin pistas de audio",
                width = 230.dp,
                options = audioOptions.map { item ->
                    TrackEntry(
                        id = item.id,
                        label = item.label,
                        selected = item.id == selectedAudioId
                    )
                },
                firstFocus = firstAudioFocus,
                onSelect = { id -> audioOptions.firstOrNull { it.id == id }?.let(onSelectAudio) }
            )

            TrackColumn(
                title = "Subtítulos",
                emptyText = "Sin subtítulos",
                width = 250.dp,
                options = subtitleOptions.map { option ->
                    TrackEntry(
                        id = option.id,
                        label = option.label,
                        selected = option.id == selectedSubtitleId
                    )
                },
                firstFocus = firstSubtitleFocus,
                onSelect = { id -> subtitleOptions.firstOrNull { it.id == id }?.let(onSelectSubtitle) }
            )

            // Columna de personalización de subtítulos: Fondo y Tamaño
            Column(
                modifier = Modifier.width(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fondo de subtítulos",
                    style = MaterialTheme.typography.labelLarge,
                    color = PastelTheme.Sky
                )
                SubtitleBackgroundPreference.entries.forEach { pref ->
                    TeleCard(
                        onClick = { onSelectSubBg(pref) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (pref == subBgPref) "● " else "  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = PillFocus
                            )
                            Text(
                                text = pref.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tamaño de letra",
                    style = MaterialTheme.typography.labelLarge,
                    color = PastelTheme.Sky
                )
                SubtitleSizePreference.entries.forEach { pref ->
                    TeleCard(
                        onClick = { onSelectSubSize(pref) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (pref == subSizePref) "● " else "  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = PillFocus
                            )
                            Text(
                                text = pref.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TrackEntry(val id: String, val label: String, val selected: Boolean)

@Composable
private fun TrackColumn(
    title: String,
    emptyText: String,
    width: Dp = 250.dp,
    options: List<TrackEntry>,
    firstFocus: FocusRequester,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = PastelTheme.Sky
        )

        if (options.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = StatusSecondary
            )
            return@Column
        }

        options.forEachIndexed { index, entry ->
            TeleCard(
                onClick = { onSelect(entry.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (entry.selected) "●" else " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = PillFocus
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Tono visual de las píldoras de control, usando colores pasteles relajantes.
 */
private enum class PillTone {
    Default,
    Primary,
    KeepActive,
    Delete
}

/**
 * Diálogo tranquilo de confirmación para eliminar completamente el archivo descargado.
 */
@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(3) {
            if (runCatching { cancelFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9090D18))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF5141A2D))
                .border(1.5.dp, PastelTheme.Coral, RoundedCornerShape(22.dp))
                .clickable(enabled = false) { }
                .padding(30.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "¿Eliminar archivo descargado?",
                    style = MaterialTheme.typography.titleLarge,
                    color = PastelTheme.Coral
                )
                Text(
                    text = "Se borrará de inmediato el video y todos los datos descargados del almacenamiento de la TV para liberar espacio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PastelTheme.TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlPill(
                        text = "Cancelar",
                        onClick = onDismiss,
                        focusRequester = cancelFocus
                    )
                    ControlPill(
                        text = "🗑 Sí, eliminar completamente",
                        onClick = onConfirm,
                        tone = PillTone.Delete
                    )
                }
            }
        }
    }
}

/**
 * Barra inferior: estado arriba, seek al medio, botones abajo.
 *
 * El fondo es un degradado pastel noche, no una banda de color plano:
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
    audioLabel: String,
    onOpenAudioTracks: () -> Unit,
    isKept: Boolean,
    onToggleKeep: () -> Unit,
    onRequestDelete: () -> Unit,
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
            .background(PastelTheme.PlayerBarGradient)
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

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    ControlPill(text = "← Volver", onClick = onBack)
                }

                item {
                    ControlPill(
                        text = if (isPlaying) "Pausa" else "▶ Reproducir",
                        onClick = onTogglePlay,
                        primary = true,
                        tone = PillTone.Primary,
                        focusRequester = playFocusRequester
                    )
                }

                item {
                    ControlPill(
                        text = if (isZoom) "Pantalla: Llenar" else "Pantalla: Ajustar",
                        onClick = onToggleZoom
                    )
                }

                item {
                    ControlPill(
                        text = "💬 $audioLabel",
                        onClick = onOpenAudioTracks
                    )
                }

                item {
                    ControlPill(
                        text = if (isKept) "✓ Archivo conservado" else "💾 Conservar archivo",
                        onClick = onToggleKeep,
                        tone = if (isKept) PillTone.KeepActive else PillTone.Default
                    )
                }

                item {
                    ControlPill(
                        text = "🗑 Eliminar completamente",
                        onClick = onRequestDelete,
                        tone = PillTone.Delete
                    )
                }
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
                    color = if (enabled) Color(0x33EDE9FE) else Color(0x1AEDEBFE),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, barHeight),
                    cornerRadius = corner
                )

                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = Color(0x66BAE6FD),
                        topLeft = Offset(0f, top),
                        size = Size(size.width * bufferedFraction, barHeight),
                        cornerRadius = corner
                    )
                }

                if (shownFraction > 0f) {
                    drawRoundRect(
                        brush = PastelTheme.SeekProgressGradient,
                        topLeft = Offset(0f, top),
                        size = Size(size.width * shownFraction, barHeight),
                        cornerRadius = corner
                    )
                }

                if (enabled && (isFocused || dragging)) {
                    val center = Offset(size.width * shownFraction, centerY)
                    drawCircle(color = SeekAccent, radius = 11.dp.toPx(), center = center)
                    drawCircle(
                        color = PastelTheme.LavenderDark,
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
 * Píldora de acción con tonos pasteles suaves y descansados para la vista.
 * Al ganar foco resalta con lavanda pastel suave y anillo claro.
 */
@Composable
private fun ControlPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    tone: PillTone = if (primary) PillTone.Primary else PillTone.Default,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "pillScale"
    )

    val shape = RoundedCornerShape(percent = 50)
    val containerColor = when {
        isFocused && tone == PillTone.Delete -> PastelTheme.Coral
        isFocused && tone == PillTone.KeepActive -> PastelTheme.Mint
        isFocused -> PillFocus
        tone == PillTone.Delete -> Color(0x33FECDD3)
        tone == PillTone.KeepActive -> PastelTheme.Mint
        tone == PillTone.Primary -> PillPrimary
        else -> PillIdle
    }

    val contentColor = when {
        isFocused && tone == PillTone.Delete -> PastelTheme.CoralDark
        isFocused && tone == PillTone.KeepActive -> PastelTheme.MintDark
        isFocused -> PillFocusText
        tone == PillTone.Delete -> PastelTheme.Coral
        tone == PillTone.KeepActive -> PastelTheme.MintDark
        tone == PillTone.Primary -> PastelTheme.LavenderDark
        else -> PillIdleText
    }

    val borderColor = when {
        isFocused && tone == PillTone.Delete -> PastelTheme.Peach
        isFocused && tone == PillTone.KeepActive -> PastelTheme.MintLight
        isFocused -> PillFocusRing
        tone == PillTone.Delete -> PastelTheme.Coral
        tone == PillTone.KeepActive -> PastelTheme.MintDark
        else -> Color(0x1F94A3B8)
    }

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
                color = borderColor,
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

/** Cola de progreso del torrent para acompañar el estado de reproducción. */
private fun progressSuffix(state: TorrentStreamState): String = when (state) {
    is TorrentStreamState.Progress ->
        " · ${state.percent.toInt()}% · ${formatRate(state.downloadRate)} · ${state.peers} peers"

    else -> ""
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
