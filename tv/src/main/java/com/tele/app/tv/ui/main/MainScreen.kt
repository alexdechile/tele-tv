package com.tele.app.tv.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tele.app.data.addon.model.HomeRow
import com.tele.app.data.addon.model.MetaPreview

/** Home de la TV: filas de carátulas navegables con el D-pad. */
@Composable
fun MainScreen(
    state: HomeState,
    onItemSelected: (MetaPreview) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (state) {
            HomeState.Loading -> CenteredMessage("Cargando catálogo…")
            is HomeState.Error -> ErrorPanel(state.message, onRetry)
            is HomeState.Content -> CatalogContent(state.rows, onItemSelected)
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = "No se pudo cargar el catálogo", style = MaterialTheme.typography.titleLarge)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(onClick = onRetry, modifier = Modifier.width(240.dp).height(64.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Reintentar", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CatalogContent(rows: List<HomeRow>, onItemSelected: (MetaPreview) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item {
            Text(
                text = "Tele TV",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        items(rows, key = { it.id }) { row ->
            CatalogRow(
                row = row,
                requestInitialFocus = row.id == rows.firstOrNull()?.id,
                onItemSelected = onItemSelected
            )
        }
    }
}

@Composable
private fun CatalogRow(
    row: HomeRow,
    requestInitialFocus: Boolean,
    onItemSelected: (MetaPreview) -> Unit
) {
    val firstCardRequester = remember { FocusRequester() }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            runCatching { firstCardRequester.requestFocus() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${row.items.size} títulos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.focusRestorer { firstCardRequester }
        ) {
            itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                PosterCard(
                    item = item,
                    initialFocusRequester = if (index == 0) firstCardRequester else null,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    }
}

/**
 * Carátula enfocable. Al ganar foco la tarjeta de tv-material se escala y
 * dibuja borde/glow, que es la señal visual de "10-foot UI".
 */
@Composable
private fun PosterCard(
    item: MetaPreview,
    initialFocusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val focusModifier = if (initialFocusRequester != null) {
        Modifier.focusRequester(initialFocusRequester)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.width(180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(180.dp)
                .height(270.dp)
                .then(focusModifier)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
