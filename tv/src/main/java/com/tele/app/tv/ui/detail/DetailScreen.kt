package com.tele.app.tv.ui.detail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tele.app.data.addon.AddonRepository
import com.tele.app.data.addon.model.Episode
import com.tele.app.data.addon.model.MetaDetail
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.Season
import com.tele.app.data.addon.model.StreamSource
import com.tele.app.data.enrich.EnrichedInfo
import com.tele.app.data.enrich.WikipediaEnricher
import com.tele.app.tv.ui.AppGraph
import com.tele.app.tv.ui.components.SkeletonDetail
import kotlinx.coroutines.CancellationException
import kotlin.math.round
import com.tele.app.tv.ui.components.TeleCard

/** Estado de la búsqueda de fuentes. */
sealed interface StreamsState {
    data object Loading : StreamsState
    data object Empty : StreamsState
    data class Error(val message: String) : StreamsState
    data class Content(val streams: List<StreamSource>) : StreamsState
}

/**
 * Detalle de una película/serie.
 *
 * - La ficha (`meta`) aporta nota, géneros, estado y **temporadas/episodios**.
 * - Una película muestra sus fuentes directamente.
 * - Una serie muestra temporadas y episodios, y las fuentes se piden para el
 *   episodio elegido (`tt...:S:E`): pedir la serie completa devolvía paquetes
 *   de temporada, que no es lo que uno quiere ver.
 */
@Composable
fun DetailScreen(
    meta: MetaPreview,
    onPlay: (StreamSource, String?) -> Unit,
    modifier: Modifier = Modifier,
    onPersonSelected: ((String) -> Unit)? = null,
    addons: AddonRepository = AppGraph.addons,
    wiki: WikipediaEnricher = AppGraph.wiki
) {
    val context = LocalContext.current

    val detail by produceState<MetaDetail?>(initialValue = null, key1 = meta.type, key2 = meta.id) {
        value = try {
            addons.detail(meta.type, meta.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }

    // Ficha cultural: llega después y nunca bloquea. Si Wikipedia falla o el
    // título no está, la pantalla queda como antes (Cinemeta).
    val enriched by produceState<EnrichedInfo?>(
        initialValue = null,
        key1 = meta.id,
        key2 = detail?.firstYear
    ) {
        value = try {
            wiki.enrich(
                metaId = meta.id,
                type = meta.type,
                title = meta.name,
                fallbackYear = detail?.firstYear ?: meta.year
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }

    var selectedEpisode by remember(meta.id) { mutableStateOf<Episode?>(null) }
    var selectedSeason by remember(meta.id) { mutableStateOf<Int?>(null) }
    var wikiExpanded by remember(meta.id) { mutableStateOf(false) }

    val seasons = detail?.seasons.orEmpty()
    val showSeasons = meta.isSeries && seasons.isNotEmpty() && selectedEpisode == null

    // Fuentes: el estado se pide acá y no dentro de la lista para poder poner
    // cada tarjeta como ítem de un único `LazyColumn`. Con una lista anidada el
    // detalle se medía con altura infinita y las fuentes se cortaban.
    val streamsId = selectedEpisode?.id ?: meta.id
    val streamsState by produceState<StreamsState?>(initialValue = null, meta.type, streamsId, showSeasons) {
        if (showSeasons) return@produceState
        value = StreamsState.Loading
        value = try {
            val streams = addons.streams(meta.type, streamsId)
            if (streams.isEmpty()) StreamsState.Empty else StreamsState.Content(streams)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            StreamsState.Error(error.message ?: "Error al buscar fuentes")
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Box(modifier = Modifier.width(220.dp).fillMaxHeight()) {
                AsyncImage(
                    model = meta.poster,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxHeight()) {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = metaLine(meta, detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                detail?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = genres.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // La sinopsis va corta y fija: el espacio vertical de una TV es
                // el recurso escaso. Lo que se corta se lee completo abajo, en
                // la lista desplazable.
                (meta.description ?: enriched?.overview)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (showSeasons) {
                    // Serie sin episodio elegido: primero la ficha cultural
                    // (compacta) y después el navegador de temporadas.
                    enriched?.takeIf { it.hasContent }?.let { info ->
                        WikipediaCard(
                            info = info,
                            detail = detail,
                            biographyLines = 1,
                            onPersonSelected = onPersonSelected,
                            onPlatformClick = { platform -> launchStreamingPlatform(context, platform) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    SeasonBrowser(
                        seasons = seasons,
                        selectedSeason = selectedSeason ?: seasons.firstOrNull { it.number > 0 }?.number,
                        onSeasonSelected = { selectedSeason = it },
                        onEpisodeSelected = { selectedEpisode = it },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    selectedEpisode?.let { episode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Pill(text = "← Episodios") { selectedEpisode = null }
                            Text(
                                text = "${episode.code} · ${episode.name.orEmpty()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    StreamsList(
                        state = streamsState,
                        onPlay = { stream ->
                            onPlay(stream, selectedEpisode?.let { "${it.code} · ${it.name}" })
                        },
                        header = enriched?.takeIf { it.hasContent },
                        detail = detail,
                        wikiExpanded = wikiExpanded,
                        // La sinopsis en español solo se despliega si arriba se
                        // está mostrando la descripción en inglés de Cinemeta.
                        wikiOverviewLines = if (meta.description != null) 8 else 0,
                        onToggleWiki = { wikiExpanded = !wikiExpanded },
                        onPersonSelected = onPersonSelected,
                        onPlatformClick = { platform -> launchStreamingPlatform(context, platform) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Ficha cultural (Wikidata/Wikipedia) bajo la sinopsis.
 *
 * En modo compacto, muestra un resumen textual para no entorpecer el D-pad hacia
 * las fuentes. Al desplegar "Ver ficha completa", el director y el reparto se
 * convierten en fichas interactivas para explorar su filmografía, y se muestran
 * premios y plataformas oficiales de streaming (Filmin, MUBI, etc.).
 */
@Composable
private fun WikipediaCard(
    info: EnrichedInfo,
    detail: MetaDetail? = null,
    expanded: Boolean = false,
    biographyLines: Int = 2,
    castLines: Int = 1,
    overviewLines: Int = 0,
    onPersonSelected: ((String) -> Unit)? = null,
    onPlatformClick: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        info.creditLine().takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Plataformas oficiales (Filmin, MUBI, Netflix...)
        if (info.platforms.isNotEmpty()) {
            Text(
                text = "Disponible en streaming oficial:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(info.platforms.size) { index ->
                    val platform = info.platforms[index]
                    PlatformChip(
                        name = platform,
                        onClick = { onPlatformClick?.invoke(platform) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Premios y galardones
        detail?.awards?.takeIf { it.isNotBlank() }?.let { awards ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "🏆", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = awards,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Directores / Creadores
        val crewMembers = (info.crew.takeIf { it.isNotEmpty() } ?: detail?.directors).orEmpty()
        if (crewMembers.isNotEmpty()) {
            if (expanded && onPersonSelected != null) {
                Text(
                    text = "${info.crewLabel}:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(crewMembers.size) { index ->
                        val person = crewMembers[index]
                        PersonChip(
                            name = person,
                            role = "🎬",
                            onClick = { onPersonSelected(person) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Text(
                    text = "${info.crewLabel}: ${crewMembers.joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        info.biography?.takeIf { biographyLines > 0 }?.let { biography ->
            Text(
                text = biography,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = biographyLines,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Reparto
        val castMembers = (info.cast.takeIf { it.isNotEmpty() } ?: detail?.cast).orEmpty()
        if (castMembers.isNotEmpty()) {
            if (expanded && onPersonSelected != null) {
                Text(
                    text = "Reparto principal:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(castMembers.size) { index ->
                        val person = castMembers[index]
                        PersonChip(
                            name = person,
                            role = "👤",
                            onClick = { onPersonSelected(person) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                Text(
                    text = "Reparto: ${castMembers.take(5).joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = castLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Sinopsis en español de Wikipedia: arriba se muestra la de Cinemeta,
        // que viene en inglés. Solo aparece al desplegar la ficha.
        info.overview?.takeIf { overviewLines > 0 && it.isNotBlank() }?.let { overview ->
            Text(
                text = "Sinopsis (Wikipedia): $overview",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = overviewLines,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "Fuente: ${info.license}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Chip interactivo para un actor o director que abre su filmografía. */
@Composable
private fun PersonChip(
    name: String,
    role: String,
    onClick: () -> Unit
) {
    TeleCard(
        onClick = onClick,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = role, style = MaterialTheme.typography.labelSmall)
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** Chip interactivo para abrir una plataforma de streaming oficial (Filmin, etc.). */
@Composable
private fun PlatformChip(
    name: String,
    onClick: () -> Unit
) {
    val isFilmin = name.contains("Filmin", ignoreCase = true)
    val icon = when {
        isFilmin -> "🟢"
        name.contains("MUBI", ignoreCase = true) -> "🎬"
        name.contains("Netflix", ignoreCase = true) -> "🍿"
        name.contains("Apple", ignoreCase = true) -> "🍏"
        name.contains("Prime", ignoreCase = true) || name.contains("Amazon", ignoreCase = true) -> "📦"
        name.contains("Disney", ignoreCase = true) -> "✨"
        name.contains("RTVE", ignoreCase = true) -> "📺"
        else -> "▶️"
    }
    val label = if (isFilmin) "Ver en Filmin" else "Ver en $name"

    TeleCard(
        onClick = onClick,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.labelSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private fun launchStreamingPlatform(context: Context, platform: String) {
    val clean = platform.lowercase()
    val packageCandidates = when {
        clean.contains("filmin") -> listOf("com.filmin.androidtv", "com.filmin")
        clean.contains("netflix") -> listOf("com.netflix.ninja")
        clean.contains("prime") || clean.contains("amazon") -> listOf("com.amazon.amazonvideo.livingroom")
        clean.contains("disney") -> listOf("com.disney.disneyplus")
        clean.contains("mubi") -> listOf("com.mubi")
        clean.contains("rtve") -> listOf("com.rtve.alacarta")
        clean.contains("movistar") -> listOf("com.movistar.androidtv")
        clean.contains("apple") -> listOf("com.apple.atve.androidtv.appletv")
        clean.contains("hbo") || clean.contains("max") -> listOf("com.wbd.stream", "com.hbo.hbonow")
        else -> emptyList()
    }

    val pm = context.packageManager
    for (pkg in packageCandidates) {
        // En Android TV, getLeanbackLaunchIntentForPackage busca actividades con CATEGORY_LEANBACK_LAUNCHER.
        // getLaunchIntentForPackage busca solo CATEGORY_LAUNCHER (móviles/tablets).
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: run {
                if (isPackageInstalled(pm, pkg)) {
                    if (pkg == "com.filmin.androidtv") {
                        Intent().apply {
                            component = ComponentName("com.filmin.androidtv", "com.filmin.androidtv.MainActivity")
                        }
                    } else {
                        Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            `package` = pkg
                        }
                    }
                } else null
            }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                return
            } catch (_: Exception) {
                // Sigue al siguiente candidato si falla el inicio
            }
        }
    }

    Toast.makeText(context, "La app de $platform no está instalada en el TV Box", Toast.LENGTH_SHORT).show()
}

private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
    return try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
}

/** "2008 · Serie · ★ 9.5 · 5 temporadas · 49 min" */
private fun metaLine(meta: MetaPreview, detail: MetaDetail?): String {
    val parts = mutableListOf<String>()

    (detail?.releaseInfo ?: meta.releaseInfo)?.let { parts += it }
    parts += if (meta.isSeries) "Serie" else "Película"

    (detail?.rating ?: meta.rating)?.let { parts += "★ ${round(it * 10) / 10}" }

    val seasonCount = detail?.seasonCount ?: 0
    if (meta.isSeries && seasonCount > 0) {
        parts += if (seasonCount == 1) "1 temporada" else "$seasonCount temporadas"
    }

    detail?.runtime?.let { parts += it }

    return parts.joinToString(" · ")
}

// -------------------------------------------------------------------------
// Temporadas y episodios
// -------------------------------------------------------------------------

@Composable
private fun SeasonBrowser(
    seasons: List<Season>,
    selectedSeason: Int?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.first()

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(seasons.size) { index ->
                val season = seasons[index]
                val label = if (season.number == 0) "Especiales" else "T${season.number}"
                val selected = season.number == current.number
                Pill(text = if (selected) "✓ $label" else label) { onSeasonSelected(season.number) }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "${current.label} · ${current.episodes.size} episodios",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(current.episodes.size) { index ->
                val episode = current.episodes[index]
                EpisodeRow(episode = episode, onClick = { onEpisodeSelected(episode) })
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    TeleCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = episode.code,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = episode.name ?: "Episodio ${episode.episode}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            episode.released?.take(10)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Píldora enfocable (mismo patrón que el resto de la app: `Card`, que ya trae foco). */
@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    TeleCard(onClick = onClick, modifier = Modifier.height(48.dp)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// -------------------------------------------------------------------------
// Fuentes
// -------------------------------------------------------------------------

/**
 * Lista desplazable de fuentes.
 *
 * La ficha cultural entra como primer ítem de **esta misma** lista: así se va
 * para arriba al bajar a las fuentes en vez de robarle el alto fijo a la
 * pantalla. Con el control remoto se baja tarjeta por tarjeta y la lista se
 * desplaza sola.
 */
@Composable
private fun StreamsList(
    state: StreamsState?,
    onPlay: (StreamSource) -> Unit,
    header: EnrichedInfo?,
    detail: MetaDetail?,
    wikiExpanded: Boolean,
    wikiOverviewLines: Int,
    onToggleWiki: () -> Unit,
    onPersonSelected: ((String) -> Unit)?,
    onPlatformClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val firstCardRequester = remember { FocusRequester() }
    val content = state as? StreamsState.Content
    val firstStream = content?.streams?.firstOrNull()

    // La ficha cultural llega después que las fuentes. Al insertarse como primer
    // ítem, la lista se queda anclada al ítem que ya tenía el foco y la ficha
    // termina ARRIBA del área visible: en pantalla se veía un recuadro cortado
    // en el borde (`el box que se escapa`). Si el usuario todavía no se movió,
    // se vuelve al tope para que la ficha se vea.
    LaunchedEffect(header != null, content != null) {
        if (header != null && listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    // Las fuentes llegan por red, después del primer dibujo. Si aparecen cuando
    // ya no queda nada enfocado, la pantalla se ve completa pero el control
    // remoto no hace nada hasta apretar una flecha. Se enfoca la primera
    // tarjeta una sola vez, cuando la lista aparece.
    var focusGiven by remember(firstStream) { mutableStateOf(false) }
    LaunchedEffect(firstStream) {
        if (firstStream != null && !focusGiven) {
            focusGiven = true
            runCatching { firstCardRequester.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        header?.let { info ->
            item(key = "ficha") {
                Column {
                    WikipediaCard(
                        info = info,
                        detail = detail,
                        expanded = wikiExpanded,
                        biographyLines = if (wikiExpanded) 12 else 2,
                        castLines = if (wikiExpanded) 3 else 1,
                        overviewLines = if (wikiExpanded) wikiOverviewLines else 0,
                        onPersonSelected = onPersonSelected,
                        onPlatformClick = onPlatformClick
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Pill(
                        text = if (wikiExpanded) "Ocultar ficha cultural" else "Ver ficha completa",
                        onClick = onToggleWiki
                    )
                }
            }
        }

        when (state) {
            null, StreamsState.Loading -> item(key = "estado") {
                SkeletonDetail()
            }

            StreamsState.Empty -> item(key = "estado") {
                Hint("Ningún addon devolvió fuentes para este título.")
            }

            is StreamsState.Error -> item(key = "estado") {
                Hint("Error al buscar fuentes: ${state.message}")
            }

            is StreamsState.Content -> items(state.streams.size, key = { "fuente-$it" }) { index ->
                val stream = state.streams[index]
                StreamCard(
                    stream = stream,
                    onClick = { onPlay(stream) },
                    focusRequester = if (index == 0) firstCardRequester else null
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun StreamCard(
    stream: StreamSource,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    TeleCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                stream.detail?.let { detail ->
                    Text(
                        text = detail.replace("\n", " · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                stream.seeders?.let { seeders ->
                    Text(
                        text = "$seeders seeds",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stream.addon,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
