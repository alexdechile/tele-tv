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
| M4 | Motor real: sesión libtorrent nativa + servidor HTTP local con `Range` + descarga secuencial | ✅ Hecho |
| M5 | Persistencia y ajustes: Room (favoritos, historial, progreso), gestión de addons desde la UI | ⏳ Pendiente |
| M6 | Pulido TV: búsqueda por teclado en pantalla, grid de catálogo, subtítulos/audio, skeleton de carga | ⏳ Pendiente |

### Qué está verificado y qué no

Verificado en CI (`./gradlew :tv:assembleDebug :core:test`):

- Compila y empaqueta el binario nativo: el CI **falla** si `libtorrent4j.so` no queda
  dentro del APK para `arm64-v8a` y `x86_64` (un fallo así compila pero revienta en la TV).
- Servidor HTTP (`HttpStreamServerTest`): 200 completo, 206 con `Content-Range`, rango abierto,
  sufijo `bytes=-N`, `HEAD`, 416 y espera de pieza mientras se descarga.
- Mapeo de `fileOffsetInTorrent` (el archivo no empieza en el byte 0 del torrent).
- `TorrentSessionTest`: carga el nativo real, arranca la sesión y acepta el magnet con los
  mismos flags que usa el motor.

Verificado a mano contra un enjambre real (Big Buck Bunny, sin dispositivo):

- El motor elige el archivo de video correcto (ignoró `.srt` y `poster.jpg`), calcula
  `fileOffset`, aplica prioridades y descarga secuencial, y los bytes leídos del disco
  tras `havePiece` son video válido (cabecera MP4).

No verificado todavía: reproducción real en una TV/emulador, rendimiento del streaming
con buffer corto, subtítulos y selección de audio.

Aviso sobre audio: ExoPlayer reproduce AAC/MP3 nativamente, pero **AC3/E-AC3/DTS**
dependen del decodificador del televisor. Si una película se ve sin sonido, casi
siempre es eso (no el torrent) y la solución es elegir otra fuente o añadir una
extensión de decodificador.

### Deuda técnica conocida

- La inyección de dependencias es un singleton (`AppGraph`), no Hilt: así se mantuvo el CI estable.
  Migrar a Hilt cuando haya más grafo que justificar.
- `AppDatabase` (Room) está declarada pero sin entidades ni uso real (M5).
- No hay búsqueda ni catálogo completo de Cinemeta: el home muestra las primeras filas de cada addon.
- Los addons por defecto viven en `DefaultAddons.URLS` (Torrentio puede cambiar de dominio).
- Solo se incluyen los ABIs `arm64-v8a` y `x86_64` (TV reales y emulador). Añadir
  `armeabi-v7a`/`x86` sumaría ~15 MB por ABI.
- Un build de release necesita la regla ProGuard que pide libtorrent4j:
  `-keep class org.libtorrent4j.swig.libtorrent_jni {*;}`.

## Compilar y probar

El build se valida en la nube (GitHub Actions): `./gradlew :tv:assembleDebug :core:test`.
El CI publica el APK como artefacto `tele-tv-debug`.

```bash
scripts/ci-watch.sh          # espera el CI del commit actual y muestra solo los errores
```

Los tests de `:core` corren en la JVM: usan el binario nativo de **Linux** de
libtorrent4j (Gradle lo extrae y fija `java.library.path`), así se ejercita la API
real sin emulador.

## Pantallas

```
Home (filas de carátulas, foco D-pad)
  └── Detalle (sinopsis + fuentes torrent ordenadas por seeders)
        └── Reproductor (ExoPlayer sobre la URL HTTP local del motor)
```
