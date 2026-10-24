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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.StreamSource
import com.tele.app.tv.ui.AppGraph
import kotlinx.coroutines.CancellationException

/** Estado de la búsqueda de fuentes. */
sealed interface StreamsState {
    data object Loading : StreamsState
    data object Empty : StreamsState
    data class Error(val message: String) : StreamsState
    data class Content(val streams: List<StreamSource>) : StreamsState
}

/**
 * Detalle de una película/serie: sinopsis + lista de fuentes torrent.
 * Al elegir una fuente se pide al navegador abrir el reproductor.
 */
@Composable
fun DetailScreen(
    meta: MetaPreview,
    onPlay: (StreamSource) -> Unit,
    modifier: Modifier = Modifier,
    addons: AddonRepository = AppGraph.addons
) {
    val streamsState by produceState<StreamsState>(
        initialValue = StreamsState.Loading,
        key1 = meta.type,
        key2 = meta.id
    ) {
        value = try {
            val streams = addons.streams(meta.type, meta.id)
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
                    text = listOfNotNull(meta.releaseInfo, meta.type).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                meta.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Fuentes disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                StreamsList(
                    state = streamsState,
                    onPlay = onPlay,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
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
            items(state.streams, key = { stream -> stream.infoHash ?: stream.url.orEmpty() }) { stream ->
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
                        text = "👤 $seeders",
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
