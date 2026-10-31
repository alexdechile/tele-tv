package com.tele.app.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tele.app.data.addon.model.Decade
import com.tele.app.data.addon.model.MediaType
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.SearchFilters
import com.tele.app.data.addon.model.SortOrder
import kotlin.math.round

/**
 * Buscador avanzado, pensado para el control remoto.
 *
 * Nada se escribe a mano: el tipo, el género, el año, la valoración, las
 * temporadas y el orden son listas de opciones. El texto se arma con un teclado
 * en pantalla (o se deja vacío para explorar solo con filtros).
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit,
    onClearFilters: () -> Unit,
    onItemSelected: (MetaPreview) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openFilter by remember { mutableStateOf<FilterKind?>(null) }
    val chipRequesters = remember { FilterKind.entries.map { FocusRequester() } }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchHeader(state = state, onBack = onBack)

            FilterChipsRow(
                state = state,
                openFilter = openFilter,
                requesters = chipRequesters,
                onToggle = { kind -> openFilter = if (openFilter == kind) null else kind },
                onClearFilters = onClearFilters
            )

            // El panel de filtros va arriba de los resultados y **compacto**: en
            // 540 dp de alto un teclado grande dejaba a los resultados sin un
            // solo píxel (el teclado "cubría la pantalla"). Con el panel corto,
            // los resultados quedan siempre a la vista abajo y se baja con el
            // D-pad.
            openFilter?.let { kind ->
                FilterPanel(
                    kind = kind,
                    state = state,
                    filters = state.filters,
                    onFiltersChange = onFiltersChange,
                    onClose = {
                        openFilter = null
                        runCatching { chipRequesters[kind.ordinal].requestFocus() }
                    }
                )
            }

            ResultsArea(
                state = state,
                onItemSelected = onItemSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// -------------------------------------------------------------------------
// Cabecera
// -------------------------------------------------------------------------

@Composable
private fun SearchHeader(state: SearchUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Buscador",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.weight(1f))

        Chip(text = "← Volver", onClick = onBack)
    }
}

/**
 * Estado del encabezado.
 *
 * Ojo con el orden: si ya hay resultados cargados se muestra la cuenta, aunque
 * los filtros estén vacíos. Antes ganaba la rama `filters.isBlank` y el
 * encabezado decía "42 resultados" mientras la grilla mostraba "Cargando lo más
 * popular…" para siempre: los 42 quedaban invisibles.
 */
private fun statusText(state: SearchUiState): String = when {
    state.loading && state.items.isEmpty() -> "Buscando…"
    state.error != null -> "No se pudo buscar: ${state.error}"
    state.enriching -> "${state.items.size} resultados · afinando nota y temporadas…"
    state.items.isNotEmpty() -> "${state.items.size} resultados"
    state.searched -> "Sin resultados con estos filtros"
    state.filters.isBlank -> "Lo más popular de películas y series"
    else -> "Ajustá los filtros"
}

// -------------------------------------------------------------------------
// Chips de filtros
// -------------------------------------------------------------------------

private enum class FilterKind(val label: String) {
    TEXT("Texto"),
    TYPE("Tipo"),
    GENRE("Género"),
    YEAR("Año"),
    RATING("Valoración"),
    SEASONS("Temporadas"),
    SORT("Orden")
}

@Composable
private fun FilterChipsRow(
    state: SearchUiState,
    openFilter: FilterKind?,
    requesters: List<FocusRequester>,
    onToggle: (FilterKind) -> Unit,
    onClearFilters: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(FilterKind.entries.size) { index ->
            val kind = FilterKind.entries[index]
            Chip(
                text = chipText(kind, state.filters, open = openFilter == kind),
                requester = requesters[index],
                onClick = { onToggle(kind) }
            )
        }

        item {
            Chip(text = "Limpiar", onClick = onClearFilters)
        }
    }
}

private fun chipText(kind: FilterKind, filters: SearchFilters, open: Boolean): String {
    val value = when (kind) {
        FilterKind.TEXT -> filters.query.ifBlank { "sin texto" }
        FilterKind.TYPE -> typesLabel(filters.types)
        FilterKind.GENRE -> filters.genre?.let(::genreLabelEs) ?: "Cualquiera"
        FilterKind.YEAR -> filters.decade?.let(::decadeLabelEs) ?: "Cualquiera"
        FilterKind.RATING -> filters.minRating?.let { "$it o más" } ?: "Cualquiera"
        FilterKind.SEASONS -> filters.minSeasons?.let { "$it o más" } ?: "Cualquiera"
        FilterKind.SORT -> sortLabelEs(filters.sort)
    }
    val arrow = if (open) "▴" else "▾"
    return "${kind.label}: $value $arrow"
}

// -------------------------------------------------------------------------
// Paneles de opciones
// -------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    kind: FilterKind,
    state: SearchUiState,
    filters: SearchFilters,
    onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit,
    onClose: () -> Unit
) {
    PanelBox(title = kind.label) {
        when (kind) {
            FilterKind.TEXT -> TextPad(
                query = filters.query,
                onChange = { text -> onFiltersChange { it.copy(query = text) } },
                onDone = onClose
            )

            FilterKind.TYPE -> OptionRow(
                options = listOf("Ambas", "Solo películas", "Solo series"),
                selected = typesLabel(filters.types),
                onSelect = { option ->
                    val types = when (option) {
                        "Solo películas" -> setOf(MediaType.MOVIE)
                        "Solo series" -> setOf(MediaType.SERIES)
                        else -> MediaType.all()
                    }
                    onFiltersChange { it.copy(types = types) }
                    onClose()
                }
            )

            FilterKind.GENRE -> OptionRow(
                options = listOf(GENRE_ANY) + state.genres.map(::genreLabelEs),
                selected = filters.genre?.let(::genreLabelEs) ?: GENRE_ANY,
                onSelect = { option ->
                    val raw = state.genres.firstOrNull { genreLabelEs(it) == option }
                    onFiltersChange { it.copy(genre = raw) }
                    onClose()
                }
            )

            FilterKind.YEAR -> OptionRow(
                options = listOf(YEAR_ANY) + Decade.entries.map(::decadeLabelEs),
                selected = filters.decade?.let(::decadeLabelEs) ?: YEAR_ANY,
                onSelect = { option ->
                    val decade = Decade.entries.firstOrNull { decadeLabelEs(it) == option }
                    onFiltersChange { it.copy(decade = decade) }
                    onClose()
                }
            )

            FilterKind.RATING -> OptionRow(
                options = listOf(RATING_ANY, "6 o más", "7 o más", "8 o más", "9 o más"),
                selected = filters.minRating?.let { "$it o más" } ?: RATING_ANY,
                onSelect = { option ->
                    val rating = option.substringBefore(" o más").toIntOrNull()
                    onFiltersChange { it.copy(minRating = rating) }
                    onClose()
                }
            )

            FilterKind.SEASONS -> OptionRow(
                options = listOf(SEASONS_ANY, "1 o más", "2 o más", "3 o más", "5 o más"),
                selected = filters.minSeasons?.let { "$it o más" } ?: SEASONS_ANY,
                onSelect = { option ->
                    val seasons = option.substringBefore(" o más").toIntOrNull()
                    onFiltersChange { current ->
                        // Filtrar por temporadas sin series seleccionadas no devolvería nada.
                        val types = if (seasons != null && !current.types.contains(MediaType.SERIES)) {
                            setOf(MediaType.SERIES)
                        } else {
                            current.types
                        }
                        current.copy(minSeasons = seasons, types = types)
                    }
                    onClose()
                }
            )

            FilterKind.SORT -> OptionRow(
                options = SortOrder.entries.map(::sortLabelEs),
                selected = sortLabelEs(filters.sort),
                onSelect = { option ->
                    val sort = SortOrder.entries.firstOrNull { sortLabelEs(it) == option } ?: SortOrder.POPULAR
                    onFiltersChange { it.copy(sort = sort) }
                    onClose()
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelBox(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

/**
 * Opciones en **una sola fila horizontal**.
 *
 * Antes era un `FlowRow` que se envolvía solo: con 20 géneros el panel crecía a
 * media pantalla y empujaba los resultados fuera. En fila horizontal scrollea
 * de costado (que es gratis con el D-pad) y el alto queda fijo.
 */
@Composable
private fun OptionRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val rowState = rememberLazyListState()
    val selectedIndex = options.indexOf(selected)

    LaunchedEffect(options, selected) {
        // Deja la opción marcada a la vista al abrir el panel.
        val target = if (selectedIndex > 1) selectedIndex - 1 else 0
        rowState.scrollToItem(target)
    }

    LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options.size) { index ->
            val option = options[index]
            val isSelected = option == selected
            Chip(
                text = if (isSelected) "✓ $option" else option,
                onClick = { onSelect(option) }
            )
        }
    }
}

// -------------------------------------------------------------------------
// Teclado en pantalla
// -------------------------------------------------------------------------

/**
 * Teclado en pantalla, en versión compacta.
 *
 * Con teclas de 44 dp el panel se comía la mitad de la pantalla y los
 * resultados no entraban. Estas son de 30 dp: siguen siendo cómodas con el
 * D-pad (se navega igual, solo que hay más teclas por fila) y dejan ~200 dp
 * libres para la grilla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextPad(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (query.isBlank()) "\"…\"" else "\"$query\"",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LETTER_ROWS.forEach { row ->
                row.forEach { letter ->
                    KeyCap(text = letter.toString(), onClick = { onChange(query + letter) })
                }
            }
            KeyCap(text = "Espacio", wide = true, onClick = { onChange(if (query.isBlank()) query else "$query ") })
            KeyCap(text = "Borrar", wide = true, onClick = { onChange(query.dropLast(1)) })
            KeyCap(text = "Limpiar", wide = true, onClick = { onChange("") })
            KeyCap(text = "Listo", wide = true, onClick = onDone)
        }
    }
}

@Composable
private fun KeyCap(text: String, wide: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(if (wide) 84.dp else 34.dp)
            .height(30.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

// -------------------------------------------------------------------------
// Resultados
// -------------------------------------------------------------------------

@Composable
private fun ResultsArea(
    state: SearchUiState,
    onItemSelected: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            // Los resultados van PRIMERO. Si hay ítems se dibujan, tengan o no
            // filtros: el caso "filtros vacíos" es justamente "lo más popular".
            // Antes esa rama ganaba siempre y la grilla nunca aparecía.
            state.items.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items.size) { index ->
                    val item = state.items[index]
                    ResultCard(item = item, onClick = { onItemSelected(item.meta) })
                }
            }

            state.loading -> Hint("Buscando en los catálogos…")

            state.error != null -> Hint("No se pudo buscar: ${state.error}")

            state.isEmptyResult -> Hint("Sin resultados. Probá con otro género, otra década o menos filtros.")

            else -> Hint("Cargando lo más popular…")
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultCard(item: SearchItem, onClick: () -> Unit) {
    val meta = item.meta

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = meta.poster,
                    contentDescription = meta.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                item.rating?.let { rating ->
                    Badge(
                        text = "★ ${round(rating * 10) / 10}",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }

                if (meta.isSeries && (item.seasonCount ?: 0) > 0) {
                    Badge(
                        text = "${item.seasonCount} temp.",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    )
                }
            }
        }

        Text(
            text = meta.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle(meta),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun subtitle(meta: MetaPreview): String = listOfNotNull(
    meta.releaseInfo,
    if (meta.isSeries) "Serie" else "Película"
).joinToString(" · ")

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xE6000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

// -------------------------------------------------------------------------
// Piezas compartidas
// -------------------------------------------------------------------------

@Composable
private fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------------------
// Etiquetas en español
// -------------------------------------------------------------------------

private const val GENRE_ANY = "Cualquiera"
private const val YEAR_ANY = "Cualquiera"
private const val RATING_ANY = "Cualquiera"
private const val SEASONS_ANY = "Cualquiera"

private val LETTER_ROWS: List<List<Char>> = listOf(
    "ABCDEFGHIJ".toList(),
    "KLMNOPQRST".toList(),
    "UVWXYZ0123".toList(),
    "456789".toList()
)

private val GENRE_ES = mapOf(
    "Action" to "Acción",
    "Adventure" to "Aventura",
    "Animation" to "Animación",
    "Biography" to "Biografía",
    "Comedy" to "Comedia",
    "Crime" to "Crimen",
    "Documentary" to "Documental",
    "Drama" to "Drama",
    "Family" to "Familia",
    "Fantasy" to "Fantasía",
    "Film-Noir" to "Cine negro",
    "Game-Show" to "Concurso",
    "History" to "Historia",
    "Horror" to "Terror",
    "Music" to "Música",
    "Musical" to "Musical",
    "Mystery" to "Misterio",
    "News" to "Noticias",
    "Reality-TV" to "Reality",
    "Romance" to "Romance",
    "Sci-Fi" to "Ciencia ficción",
    "Short" to "Cortometraje",
    "Sport" to "Deporte",
    "Talk-Show" to "Talk show",
    "Thriller" to "Suspense",
    "War" to "Guerra",
    "Western" to "Western"
)

private fun genreLabelEs(genre: String): String = GENRE_ES[genre] ?: genre

private fun decadeLabelEs(decade: Decade): String = when (decade) {
    Decade.D2020 -> "2020s"
    Decade.D2010 -> "2010s"
    Decade.D2000 -> "2000s"
    Decade.D1990 -> "1990s"
    Decade.D1980 -> "1980s"
    Decade.BEFORE -> "Antes de 1980"
}

private fun sortLabelEs(sort: SortOrder): String = when (sort) {
    SortOrder.POPULAR -> "Popularidad"
    SortOrder.RECENT -> "Más recientes"
    SortOrder.RATED -> "Mejor valoradas"
}

private fun typesLabel(types: Set<MediaType>): String = when {
    types.size >= 2 -> "Ambas"
    types.contains(MediaType.SERIES) -> "Solo series"
    types.contains(MediaType.MOVIE) -> "Solo películas"
    else -> "Ambas"
}
