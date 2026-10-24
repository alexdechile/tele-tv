package com.tele.app.data.addon

import com.tele.app.data.addon.model.AddonCatalog
import com.tele.app.data.addon.model.AddonManifest
import com.tele.app.data.addon.model.HomeRow
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
 * - Para el home: cada addon que declare el recurso `catalog` aporta filas.
 * - Para el detalle: cada addon que declare el recurso `stream` aporta fuentes.
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
    private val cacheMutex = Mutex()

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

    /** Fuentes reproducibles para un ítem, ordenadas por seeders. */
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
            client.catalog(addonUrl, catalog, limit = itemsPerRow)
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
        const val RESOURCE_STREAM = "stream"
        const val TYPE_MOVIE = "movie"
        const val TYPE_SERIES = "series"
    }
}
