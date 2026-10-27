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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    addons: AddonRepository = AppGraph.addons
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

    var selectedEpisode by remember(meta.id) { mutableStateOf<Episode?>(null) }
    var selectedSeason by remember(meta.id) { mutableStateOf<Int?>(null) }

    val seasons = detail?.seasons.orEmpty()

    Surface(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            Box(modifier = Modifier.width(240.dp).fillMaxHeight()) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = metaLine(meta, detail),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                detail?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = genres.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                meta.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        meta.isSeries && seasons.isNotEmpty() && selectedEpisode == null -> SeasonBrowser(
                            seasons = seasons,
                            selectedSeason = selectedSeason ?: seasons.firstOrNull { it.number > 0 }?.number,
                            onSeasonSelected = { selectedSeason = it },
                            onEpisodeSelected = { selectedEpisode = it }
                        )

                        else -> Column(modifier = Modifier.fillMaxSize()) {
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
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            StreamsSection(
                                addons = addons,
                                type = meta.type,
                                id = selectedEpisode?.id ?: meta.id,
                                onPlay = { stream ->
                                    onPlay(stream, selectedEpisode?.let { "${it.code} · ${it.name}" })
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
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
    onEpisodeSelected: (Episode) -> Unit
) {
    val current = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.first()

    Column(modifier = Modifier.fillMaxSize()) {
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

@Composable
private fun StreamsSection(
    addons: AddonRepository,
    type: String,
    id: String,
    onPlay: (StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by produceState<StreamsState>(initialValue = StreamsState.Loading, key1 = type, key2 = id) {
        value = try {
            val streams = addons.streams(type, id)
            if (streams.isEmpty()) StreamsState.Empty else StreamsState.Content(streams)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            StreamsState.Error(error.message ?: "Error al buscar fuentes")
        }
    }

    StreamsList(state = state, onPlay = onPlay, modifier = modifier)
}

@Composable
private fun StreamsList(
    state: StreamsState,
    onPlay: (StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        StreamsState.Loading -> Box(modifier = modifier) {
            Text(
                text = "Buscando fuentes en los addons…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        StreamsState.Empty -> Box(modifier = modifier) {
            Text(
                text = "Ningún addon devolvió fuentes para este título.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is StreamsState.Error -> Box(modifier = modifier) {
            Text(
                text = "Error al buscar fuentes: ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is StreamsState.Content -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            items(state.streams.size) { index ->
                val stream = state.streams[index]
                StreamCard(stream = stream, onClick = { onPlay(stream) })
            }
        }
    }
}

@Composable
private fun StreamCard(stream: StreamSource, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
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
