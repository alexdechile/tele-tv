# Tele TV

App de streaming de video por torrents para **Android TV** (solo TV: no móvil, no touch).

- UI: Kotlin + **Jetpack Compose for TV** (`androidx.tv.material3`), paradigma *10-foot UI*.
- Reproductor: **Media3 / ExoPlayer** (`PlayerView` vía `AndroidView`, porque `media3-ui-compose` no existe).
- Motor de torrents: **LibTorrent4j**, con un servidor HTTP local que sirve el archivo a ExoPlayer
  (ExoPlayer nunca sabe que hay un torrent detrás).
- Contenido: **protocolo de addons de Stremio v3** (Cinemeta para catálogos, Torrentio para fuentes).

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `:core` | Motor de torrents (`TorrentEngine`) y servidor HTTP local. |
| `:data` | Cliente del protocolo Stremio v3, repositorio de addons, modelos. |
| `:tv`  | App Android TV: Compose for TV, navegación, reproductor, ViewModels. |

## Estado del proyecto

| Milestone | Alcance | Estado |
|---|---|---|
| M1 | Fundación: repo, 3 módulos, manifest solo-TV, CI en GitHub Actions | ✅ Hecho |
| M2 | Motor de torrents (LibTorrent4j) + reproductor ExoPlayer + CI verde | ✅ Hecho |
| M3 | Cliente de addons Stremio v3, home con foco D-pad, detalle con fuentes, reproductor real | ✅ Hecho |
| M4 | Motor real: sesión libtorrent nativa + servidor HTTP local con `Range` + descarga secuencial | ⏳ Próximo |
| M5 | Persistencia y ajustes: Room (favoritos, historial, progreso), gestión de addons desde la UI | ⏳ Pendiente |
| M6 | Pulido TV: búsqueda por teclado en pantalla, grid de catálogo, subtítulos/audio, skeleton de carga | ⏳ Pendiente |

### Deuda técnica conocida

- `:core/TorrentEngine` es un **stub**: emite `http://127.0.0.1:0/stream` sin arrancar libtorrent.
  Por eso el reproductor abre pero no reproduce todavía (es el objetivo de M4).
- La inyección de dependencias es un singleton (`AppGraph`), no Hilt: así se mantuvo el CI estable.
  Migrar a Hilt cuando haya más grafo que justificar.
- `AppDatabase` (Room) está declarada pero sin entidades ni uso real (M5).
- No hay búsqueda ni catálogo completo de Cinemeta: el home muestra las primeras filas de cada addon.
- Los addons por defecto viven en `DefaultAddons.URLS` (Torrentio puede cambiar de dominio).

## Compilar

El build se valida en la nube (GitHub Actions): `./gradlew :tv:assembleDebug`.

```bash
scripts/ci-watch.sh          # espera el CI del commit actual y muestra solo los errores
```

## Pantallas

```
Home (filas de carátulas, foco D-pad)
  └── Detalle (sinopsis + fuentes torrent ordenadas por seeders)
        └── Reproductor (ExoPlayer sobre la URL HTTP local del motor)
```
