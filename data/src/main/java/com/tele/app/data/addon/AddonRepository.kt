package com.tele.app.data.addon

import com.tele.app.data.addon.model.AddonCatalog
import com.tele.app.data.addon.model.AddonManifest
import com.tele.app.data.addon.model.Decade
import com.tele.app.data.addon.model.HomeRow
import com.tele.app.data.addon.model.MediaType
import com.tele.app.data.addon.model.MetaDetail
import com.tele.app.data.addon.model.MetaPreview
import com.tele.app.data.addon.model.SearchFilters
import com.tele.app.data.addon.model.SortOrder
import com.tele.app.data.addon.model.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orquesta varios addons Stremio al mismo tiempo.
 *
 * - Home: cada addon que declare el recurso `catalog` aporta filas.
 * - Buscador: los filtros que el addon soporta viajan en la URL (`search`,
 *   `genre`, `skip`); lo que no soporta se filtra en el cliente con la ficha.
 * - Detalle: `meta` aporta nota, géneros, temporadas y episodios; `stream` aporta
 *   las fuentes (de la película o del episodio exacto).
 *
 * Un addon caído nunca rompe la pantalla: se descarta y se sigue con el resto.
 */
class AddonRepository(
    private val client: StremioAddonClient = StremioAddonClient(),
    private val addonUrls: List<String> = DefaultAddons.URLS,
    private val itemsPerRow: Int = 20,
    private val maxRowsPerAddon: Int = 2,
    private val maxRows: Int = 6
) {

    private val manifestCache = mutableMapOf<String, AddonManifest>()
    private val detailCache = object : LinkedHashMap<String, MetaDetail>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MetaDetail>?) =
            size > MAX_CACHED_DETAILS
    }
    private val cacheMutex = Mutex()

    // ---------------------------------------------------------------------
    // Home
    // ---------------------------------------------------------------------

    /** Filas del home, agregando el contenido de todos los addons configurados. */
    suspend fun homeRows(): List<HomeRow> = coroutineScope {
        addonUrls
            .map { addonUrl ->
                async {
                    val manifest = manifest(addonUrl) ?: return@async emptyList<HomeRow>()
                    if (!manifest.provides(RESOURCE_CATALOG)) return@async emptyList<HomeRow>()
                    buildRows(addonUrl, manifest)
                }
            }
            .awaitAll()
            .flatten()
            .take(maxRows)
    }

    // ---------------------------------------------------------------------
    // Fuentes
    // ---------------------------------------------------------------------

    /**
     * Fuentes reproducibles para un ítem, ordenadas por seeders.
     *
     * [id] puede ser de película (`tt0133093`), de serie completa o de episodio
     * (`tt0903747:1:1`): Torrentio devuelve cosas distintas para cada uno.
     */
    suspend fun streams(type: String, id: String): List<StreamSource> = coroutineScope {
        val sources = addonUrls
            .map { addonUrl ->
                async {
                    val manifest = manifest(addonUrl) ?: return@async emptyList<StreamSource>()
                    if (!manifest.provides(RESOURCE_STREAM)) return@async emptyList<StreamSource>()
                    try {
                        client.streams(addonUrl, manifest.name, type, id)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        emptyList()
                    }
                }
            }
            .awaitAll()
            .flatten()

        sources
            .distinctBy { it.infoHash ?: it.url }
            .sortedByDescending { it.seeders ?: 0 }
    }

    // ---------------------------------------------------------------------
    // Buscador
    // ---------------------------------------------------------------------

    /**
     * Candidatos para el buscador avanzado.
     *
     * Lo que el protocolo sabe hacer se pide al addon (texto, género, paginado);
     * la década se filtra acá porque el catálogo solo entrega un año exacto por
     * ítem. La nota y las temporadas **no** se filtran en este punto: eso exige
     * la ficha de cada título, y el ViewModel la pide de forma progresiva con
     * [detail] para que la pantalla no quede en negro esperando N respuestas.
     */
    suspend fun search(filters: SearchFilters): List<MetaPreview> = coroutineScope {
        if (filters.types.isEmpty()) return@coroutineScope emptyList()

        val pages = if (filters.decade != null || filters.needsDetail) WIDE_PAGES else NARROW_PAGES

        val batches = filters.types.flatMap { type ->
            addonUrls.map { addonUrl ->
                async { searchIn(addonUrl, type, filters, pages) }
            }
        }

        val unique = LinkedHashMap<String, MetaPreview>()
        batches.awaitAll().flatten().forEach { meta -> unique.putIfAbsent(meta.id, meta) }

        val filtered = unique.values.filter { meta -> matchesDecade(meta, filters.decade) }

        when (filters.sort) {
            SortOrder.POPULAR, SortOrder.RATED -> filtered
            SortOrder.RECENT -> filtered.sortedByDescending { it.year ?: 0 }
        }
    }

    /**
     * Géneros que ofrecen los addons para los tipos pedidos.
     *
     * Salen del manifest (no de una lista fija): si mañana se agrega otro addon
     * con sus propios géneros, el buscador los muestra sin tocar código.
     *
     * Más [UNLISTED_GENRES]: el catálogo de Cinemeta acepta algunos géneros que
     * **no anuncia** en su manifest.
     */
    suspend fun genres(types: Set<MediaType> = MediaType.all()): List<String> = coroutineScope {
        val wanted = types.map { it.apiType }.toSet()
        val fromManifests = addonUrls
            .map { addonUrl -> async { manifest(addonUrl) } }
            .awaitAll()
            .filterNotNull()
            .flatMap { manifest ->
                manifest.catalogs
                    .filter { it.type in wanted }
                    .flatMap { it.genres }
            }

        (fromManifests + UNLISTED_GENRES).distinct().sorted()
    }

    // ---------------------------------------------------------------------
    // Ficha (meta)
    // ---------------------------------------------------------------------

    /** Ficha completa de un título, con caché para no repetirla al filtrar. */
    suspend fun detail(type: String, id: String): MetaDetail? {
        val key = "$type:$id"
        cacheMutex.withLock { detailCache[key] }?.let { return it }

        val detail = addonUrls.firstNotNullOfOrNull { addonUrl ->
            val manifest = manifest(addonUrl) ?: return@firstNotNullOfOrNull null
            if (!manifest.provides(RESOURCE_META)) return@firstNotNullOfOrNull null
            try {
                client.meta(addonUrl, type, id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                null
            }
        } ?: return null

        cacheMutex.withLock { detailCache[key] = detail }
        return detail
    }

    suspend fun detail(meta: MetaPreview): MetaDetail? = detail(meta.type, meta.id)

    // ---------------------------------------------------------------------
    // Internos
    // ---------------------------------------------------------------------

    private suspend fun searchIn(
        addonUrl: String,
        type: MediaType,
        filters: SearchFilters,
        pages: Int
    ): List<MetaPreview> {
        val manifest = manifest(addonUrl) ?: return emptyList()
        if (!manifest.provides(RESOURCE_CATALOG)) return emptyList()

        val catalog = pickCatalog(manifest, type, filters) ?: return emptyList()

        val collected = mutableListOf<MetaPreview>()
        for (page in 0 until pages) {
            val extras = buildMap {
                put(EXTRA_SKIP, (page * PAGE_SIZE).toString())
                if (filters.query.isNotBlank()) put(EXTRA_SEARCH, filters.query.trim())
                filters.genre?.let { put(EXTRA_GENRE, it) }
            }

            val items = try {
                client.catalog(addonUrl, type.apiType, catalog.id, extras)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                emptyList()
            }

            if (items.isEmpty()) break
            collected += items
            // Un addon que devuelve menos de una página ya no tiene más resultados:
            // pedir la siguiente solo gasta red (y en Cinemeta la búsqueda ni pagina).
            if (items.size < PAGE_SIZE) break
        }

        return collected
    }

    /** Elige el catálogo que mejor soporta los filtros pedidos. */
    private fun pickCatalog(
        manifest: AddonManifest,
        type: MediaType,
        filters: SearchFilters
    ): AddonCatalog? {
        val ofType = manifest.catalogs.filter { it.type == type.apiType }
        if (ofType.isEmpty()) return null

        val byId = ofType.firstOrNull { it.id == POPULAR_CATALOG }

        return when {
            filters.query.isNotBlank() ->
                ofType.firstOrNull { it.supportsSearch } ?: byId ?: ofType.first()

            filters.genre != null ->
                ofType.firstOrNull { it.genres.isNotEmpty() } ?: byId ?: ofType.first()

            else -> byId ?: ofType.first()
        }
    }

    private fun matchesDecade(meta: MetaPreview, decade: Decade?): Boolean {
        if (decade == null) return true
        val year = meta.year ?: return false
        return decade.contains(year)
    }

    private suspend fun buildRows(addonUrl: String, manifest: AddonManifest): List<HomeRow> =
        coroutineScope {
            manifest.catalogs
                .filter { it.type == TYPE_MOVIE || it.type == TYPE_SERIES }
                .take(maxRowsPerAddon)
                .map { catalog ->
                    async { rowFor(addonUrl, manifest, catalog) }
                }
                .awaitAll()
                .filterNotNull()
        }

    private suspend fun rowFor(
        addonUrl: String,
        manifest: AddonManifest,
        catalog: AddonCatalog
    ): HomeRow? {
        val items = try {
            client.catalog(
                addonUrl = addonUrl,
                type = catalog.type,
                catalogId = catalog.id,
                extras = mapOf(EXTRA_SKIP to "0")
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            emptyList()
        }

        if (items.isEmpty()) return null

        return HomeRow(
            id = "${manifest.id}:${catalog.type}:${catalog.id}",
            title = rowTitle(catalog),
            items = items.take(itemsPerRow)
        )
    }

    private fun rowTitle(catalog: AddonCatalog): String {
        val typeLabel = when (catalog.type) {
            TYPE_MOVIE -> "Películas"
            TYPE_SERIES -> "Series"
            else -> catalog.type
        }
        return "${catalog.name} · $typeLabel"
    }

    private suspend fun manifest(addonUrl: String): AddonManifest? = cacheMutex.withLock {
        manifestCache[addonUrl] ?: try {
            client.manifest(addonUrl).also { manifestCache[addonUrl] = it }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
    }

    private companion object {
        const val RESOURCE_CATALOG = "catalog"
        const val RESOURCE_META = "meta"
        const val RESOURCE_STREAM = "stream"
        const val TYPE_MOVIE = MetaPreview.TYPE_MOVIE
        const val TYPE_SERIES = MetaPreview.TYPE_SERIES

        const val EXTRA_SKIP = "skip"
        const val EXTRA_SEARCH = "search"
        const val EXTRA_GENRE = "genre"

        /**
         * Géneros que el catálogo sí sirve pero no publica en el manifest.
         *
         * Comprobado contra la API real (`/catalog/movie/top/genre=Music.json` y
         * `genre=Musical.json`): los dos devuelven 50 títulos. Sin esto el
         * buscador nunca ofrecía "Música" y no había forma de pedir videos de
         * música, aunque Cinemeta los tenga.
         */
        val UNLISTED_GENRES = listOf("Music", "Musical")

        /** El catálogo "Popular" de Cinemeta; es el que pagina y acepta búsqueda. */
        const val POPULAR_CATALOG = "top"

        const val PAGE_SIZE = 50
        const val NARROW_PAGES = 1
        const val WIDE_PAGES = 2
        const val MAX_CACHED_DETAILS = 300
    }
}
