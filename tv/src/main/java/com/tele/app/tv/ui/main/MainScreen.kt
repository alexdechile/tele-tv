package com.tele.app.tv.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tele.app.data.addon.model.HomeRow
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.tv.ui.components.SkeletonCatalog
import com.tele.app.tv.ui.components.TeleCard

/** Home de la TV: filas de carátulas navegables con el D-pad. */
@Composable
fun MainScreen(
    state: HomeState,
    onItemSelected: (MetaPreview) -> Unit,
    onOpenSearch: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        when (state) {
            // Esqueleto en vez de "Cargando catálogo…": la forma de lo que viene
            // dice "está trabajando", no "se colgó".
            HomeState.Loading -> SkeletonCatalog()
            is HomeState.Error -> ErrorPanel(state.message, onRetry)
            is HomeState.Content -> CatalogContent(state.rows, onItemSelected, onOpenSearch)
        }
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
            TeleCard(onClick = onRetry, modifier = Modifier.width(240.dp).height(64.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Reintentar", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Catálogo con dos formas de mirarlo:
 *
 *  - **filas**, la de siempre: un carrusel por categoría, para picotear;
 *  - **cuadrícula**, todo el catálogo junto, para barrer con el D-pad cuando ya
 *    se sabe qué buscar.
 *
 * El modo vive en `rememberSaveable`: volver del detalle no lo devuelve a filas.
 * No se guarda en disco a propósito: es una preferencia de sesión y la app no
 * persiste nada.
 */
@Composable
private fun CatalogContent(
    rows: List<HomeRow>,
    onItemSelected: (MetaPreview) -> Unit,
    onOpenSearch: () -> Unit
) {
    var gridMode by rememberSaveable { mutableStateOf(false) }

    val header: @Composable () -> Unit = {
        HeaderRow(
            onOpenSearch = onOpenSearch,
            gridMode = gridMode,
            onToggleGrid = { gridMode = !gridMode }
        )
    }

    if (gridMode) {
        val allItems = remember(rows) { rows.flatMap { it.items }.distinctBy { it.id } }
        val firstCardRequester = remember { FocusRequester() }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.padding(bottom = 12.dp)) { header() }
            }
            gridItemsIndexed(allItems, key = { _, item -> item.id }) { index, item ->
                PosterCard(
                    item = item,
                    initialFocusRequester = if (index == 0) firstCardRequester else null,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item { header() }
            items(rows, key = { it.id }) { row ->
                CatalogRow(
                    row = row,
                    requestInitialFocus = row.id == rows.firstOrNull()?.id,
                    onItemSelected = onItemSelected
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(
    onOpenSearch: () -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Tele TV",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Un buscador avanzado para encontrar qué ver",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TeleCard(onClick = onToggleGrid, modifier = Modifier.width(260.dp).height(64.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (gridMode) "Vista: Filas" else "Vista: Cuadrícula",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        TeleCard(onClick = onOpenSearch, modifier = Modifier.width(240.dp).height(64.dp)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Buscar en el catálogo", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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
        TeleCard(
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
