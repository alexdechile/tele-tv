# Tele TV

App de streaming de video por torrents para **Android TV**, y el mismo APK sirve en **tablet**
(tacto y mouse, no solo D-pad). Un solo binario: la UI es la misma de 10 pies, lo que cambia
por dispositivo es la entrada (ver `TeleCard`) y las barras del sistema.

- UI: Kotlin + **Jetpack Compose for TV** (`androidx.tv.material3`), paradigma *10-foot UI*.
- Reproductor: **Media3 / ExoPlayer** (`PlayerView` vía `AndroidView`, porque `media3-ui-compose` no existe).
- Motor de torrents: **jlibtorrent** (libtorrent 2.0, proyecto frostwire), con un servidor HTTP
  local que sirve el archivo a ExoPlayer (ExoPlayer nunca sabe que hay un torrent detrás).
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
| M2 | Motor de torrents (jlibtorrent) + reproductor ExoPlayer + CI verde | ✅ Hecho |
| M3 | Cliente de addons Stremio v3, home con foco D-pad, detalle con fuentes, reproductor real | ✅ Hecho |
| M4 | Motor real: sesión libtorrent nativa + servidor HTTP local con `Range` + descarga secuencial | ✅ Hecho |
| M5 | Persistencia y ajustes: Room (favoritos, historial, progreso), gestión de addons desde la UI | ⏭️ Saltado por decisión de Alex: no quiere que la app guarde datos; se desinstala e instala |
| M6 | Pulido TV: búsqueda por teclado en pantalla, grid de catálogo, subtítulos/audio, skeleton de carga | ✅ Hecho |

### Qué está verificado y qué no

Verificado en CI (`./gradlew :tv:assembleDebug :core:test`):

- Compila y empaqueta el binario nativo: el CI **falla** si `libjlibtorrent.so` no queda
  dentro del APK para `arm64-v8a`, `armeabi-v7a` y `x86_64` (un fallo así compila pero
  revienta en la TV).
- Servidor HTTP (`HttpStreamServerTest`): 200 completo, 206 con `Content-Range`, rango abierto,
  sufijo `bytes=-N`, `HEAD`, 416 y espera de pieza mientras se descarga.
- Mapeo de `fileOffsetInTorrent` (el archivo no empieza en el byte 0 del torrent).
- `TorrentSessionTest`: carga el nativo real, arranca la sesión y acepta el magnet con los
  mismos flags que usa el motor.
- `StreamCompletionTest`: la subida se corta cuando el archivo elegido está entero en disco,
  y no antes. Se mide con los bytes **del archivo** (`fileProgress`), no con el promedio del
  torrent, que con los demás archivos en `IGNORE` nunca llega a 100 %.
- `TorrentSubtitlesTest`: reconoce `.srt`/`.vtt`/`.ass` y saca el idioma del nombre del
  release (`Pelicula.2023.es.srt` → español), sin confundir `It.2017.srt` con italiano.
- `HttpStreamServerTest`: además del video, sirve los subtítulos sueltos por `/sub/<n>` y
  responde 404 a cualquier otra ruta.
- `TrackLabelsTest` (`:tv:test`): etiquetas del menú de pistas ("Español · AC3 · 5.1",
  "Inglés [SDH]"), que es lo que hace usable el selector de audio y subtítulos.

Verificado a mano contra un enjambre real (Big Buck Bunny, sin dispositivo):

- El motor elige el archivo de video correcto (ignoró `.srt` y `poster.jpg`), calcula
  `fileOffset`, aplica prioridades y descarga secuencial, y los bytes leídos del disco
  tras `havePiece` son video válido (cabecera MP4).

No verificado todavía: reproducción real en una TV/emulador, rendimiento del streaming
con buffer corto, subtítulos y selección de audio.

### M6: pulido de TV

- **Vista de catálogo**: botón en la cabecera que alterna entre filas (carruseles por
  categoría) y **cuadrícula** con todo el catálogo, navegable con D-pad.
- **Skeletons**: el home, la grilla del buscador y la búsqueda de fuentes dibujan la forma de
  lo que va a llegar (carátulas, filas, fichas) en vez de un texto centrado.
- **Menú de pistas**: botones "Audio: …" y "Subtítulos: …" abren un menú con las pistas del
  contenedor, con idioma, códec y canales (`Español · AC3 · 5.1`). Ya no se ciclan a ciegas.
- **Subtítulos sueltos del torrent**: los `.srt`/`.vtt`/`.ass` que acompañan al video se
  detectan, se bajan y el servidor local los sirve en `/sub/<n>`; ExoPlayer los carga como
  subtítulos externos con su idioma (deducido del nombre del release). El español se
  elige solo si existe.

### Audio en TV box (AC3/E-AC3/DTS)

Hallazgo importante: el sink de Media3 por defecto (`DefaultAudioSink.Builder(context)`)
arranca con `AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES`, que **solo anuncia PCM**.
Con esa configuración ExoPlayer nunca ofrece passthrough y, en una fuente AC3 sin
decoder de plataforma, el resultado es video con silencio.

Por eso `:tv` construye el reproductor con `AudioAwareRenderersFactory`, que inyecta
`AudioCapabilities.getCapabilities(context)`. Con eso ExoPlayer prefiere, en este orden:

1. **Passthrough (bitstream)** si el televisor/receptor acepta AC3 por HDMI (`AudioCapabilities`).
2. **Decodificador de plataforma** (`MediaCodecList`), típico en chips Amlogic con licencia.

(Si ninguno está disponible, el overlay lo dice con `⚠ elige otra fuente`.)

El overlay del reproductor muestra qué pista trae la fuente y por qué camino sale
(`Audio: AC3 (passthrough HDMI)`), y avisa con `⚠ elige otra fuente` si el equipo no
puede con ese audio. El detalle completo queda en logcat con la etiqueta `TeleTvAudio`.

### Deuda técnica conocida

- La inyección de dependencias es un singleton (`AppGraph`), no Hilt: así se mantuvo el CI estable.
  Migrar a Hilt cuando haya más grafo que justificar.
- `AppDatabase` (Room) está declarada pero sin entidades ni uso real (M5).
- No hay búsqueda ni catálogo completo de Cinemeta: el home muestra las primeras filas de cada addon.
- Los addons por defecto viven en `DefaultAddons.URLS` (Torrentio puede cambiar de dominio).
- Se incluyen los ABIs `arm64-v8a`, `armeabi-v7a` (TV boxes Amlogic/32-bit) y `x86_64` (emulador).
- Un build de release **no** necesita reglas ProGuard propias: el AAR de jlibtorrent trae su
  `proguard.txt` de consumidor (keep de `com.frostwire.jlibtorrent.swig.libtorrent_jni` y de los
  directores que C++ llama por nombre), y AGP lo aplica solo.

## BitTorrent v2 y v3.1

El motor corre sobre **libtorrent 2.0** (jlibtorrent 2.0.15.3), la primera generación de
libtorrent con soporte nativo del protocolo v2. Qué está cubierto y qué no, medido contra
la API que realmente expone el AAR (no contra la documentación):

| Capacidad | Estado |
|---|---|
| Magnet con info-hash v2 (`btmh:`, SHA-256) | ✅ `AddedTorrentParams.parseMagnetUri` |
| Torrents híbridos v1+v2 | ✅ |
| Árbol de archivos y *piece layers* de v2 | ✅ los maneja libtorrent |
| Info-hash v2 al leer metadatos | ✅ `TorrentInfo.infoHashV2()` |
| Elegir v2 y caer a v1 si no hay | ✅ `TorrentEngine.awaitTorrent` |
| Búsquedas DHT con hash truncado + sal (BEP-53) | ✅ `dht_privacy_lookups = true` |
| Cifrado obligatorio entre pares | ⚠️ opt-in con `requireEncryption`; libtorrent usa **MSE/PE (RC4)**, no TLS/MuTP |
| `info_pow` (Proof-of-Work de BEP-53) | ❌ libtorrent 2.0.15.3 no lo expone en ningún lado |

Sobre las dos últimas filas, que son las que de verdad separan v2 de v3.1:

- **El cifrado obligatorio viene apagado.** `pe_forced` rechaza a todo par que no negocie
  el handshake cifrado. En enjambres públicos de torrents v1 eso deja el video buffereando,
  así que el perfil estricto es una opción, no el default. El cifrado que implementa
  libtorrent ofusca el contenido frente a quien mira el cable, pero RC4 no es criptografía
  moderna: no conviene venderlo como "v3.1 completo".
- **`info_pow` no se verifica.** No hay campo ni método en las clases SWIG de jlibtorrent
  2.0.15.3 (`TorrentInfo`, `TorrentHandle`, `settings_pack`) que lo toque. Esto **no rompe**
  la reproducción: el campo vive dentro del diccionario `info` y entra en el hash v2, pero
  los clientes lo tratan como una prueba de esfuerzo del editor, no como requisito de
  conexión. Lo que falta es poder comprobar la dificultad que declara; queda anotado para
  cuando libtorrent la implemente.

## Compilar y probar

El build se valida en la nube (GitHub Actions): `./gradlew :tv:assembleDebug :core:test`.
El CI publica el APK como artefacto `tele-tv-debug`.

```bash
scripts/ci-watch.sh          # espera el CI del commit actual y muestra solo los errores
```

Los tests de `:core` corren en la JVM: usan el binario nativo de **Linux**
(`jlibtorrent-linux-x86_64`, que Gradle extrae y fija en `java.library.path`), así se ejercita
la API real sin emulador. La app compila contra el AAR `jlibtorrent-android`, que trae las
mismas clases Java más las `.so` de las cuatro ABIs.

## Pantallas

```
Home (filas de carátulas, foco D-pad)
  └── Detalle (sinopsis + fuentes torrent ordenadas por seeders)
        └── Reproductor (ExoPlayer sobre la URL HTTP local del motor)
```
