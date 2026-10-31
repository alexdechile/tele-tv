package com.tele.app.tv.ui.detail

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
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
import kotlinx.coroutines.CancellationException
import kotlin.math.round

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
    addons: AddonRepository = AppGraph.addons,
    wiki: WikipediaEnricher = AppGraph.wiki
) {
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
                        WikipediaCard(info, biographyLines = 1)
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
                        wikiExpanded = wikiExpanded,
                        onToggleWiki = { wikiExpanded = !wikiExpanded },
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
 * Todo es texto plano ya limpiado en `:data`: no hay HTML, ni WebView, ni
 * enlaces clicables. Tampoco agrega botones, así que la navegación con el
 * control remoto sigue exactamente igual que antes de esta sección.
 *
 * Las líneas son deliberadamente cortas: en una TV de 540 dp de alto cada línea
 * de más empuja las fuentes fuera de la pantalla (ese fue el bug: la ficha se
 * quedaba con el espacio y las fuentes aparecían cortadas al fondo).
 */
@Composable
private fun WikipediaCard(info: EnrichedInfo, biographyLines: Int = 2, castLines: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        info.creditLine().takeIf { it.isNotBlank() }?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (info.crew.isNotEmpty()) {
            Text(
                text = "${info.crewLabel}: ${info.crewLine()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

        if (info.cast.isNotEmpty()) {
            Text(
                text = "Reparto: ${info.castLine()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = castLines,
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
    Card(
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
    Card(onClick = onClick, modifier = Modifier.height(48.dp)) {
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
    wikiExpanded: Boolean,
    onToggleWiki: () -> Unit,
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
                        biographyLines = if (wikiExpanded) 12 else 2,
                        castLines = if (wikiExpanded) 3 else 1
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
                Hint("Buscando fuentes en los addons…")
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
    Card(
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
