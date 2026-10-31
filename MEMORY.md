# Tele TV - Memoria y Estado del Proyecto

## Estado Actual de la Sesión (2026-10-30, noche)
- **Últimos commits:** `d4b7807` (sinopsis al desplegar la ficha), `c90ecf5` (botón de ficha + género Música),
  `c4aee2b` (foco de la primera fuente), `5a82340` (la ficha no se escapa), `1ea0fd0` (buscador y detalle),
  `0eaeef9` (inicio del archivo primero).
- **Estado del TV Box:** MECOOL KM6 conectado y **verificado en dispositivo**. Instalado `d4b7807`.
- **Cómo encontrarlo:** `scripts/tvbox.sh` escanea la subred por el puerto 5555 y confirma el modelo KM6.
  El equipo tiene **dos interfaces activas**: Wi-Fi `192.168.1.94` y cable `192.168.1.128`; la IP
  depende de cuál esté en uso y cambia por DHCP. Reservar la IP en el router para que no se pierda.
- **Verificado en el KM6 (no solo en CI):** la ficha cultural sale con datos reales (director,
  bio, reparto, productora, país, idioma, atribución CC BY-SA) y el video **arranca a los 26 s**
  con la caché vacía, reproduciendo sin barras ni recortes.

## Música en el buscador (2026-10-30)

- **Lo que sí hay:** Cinemeta acepta `genre=Music` y `genre=Musical` **aunque no los anuncie en su
  manifest** (`/catalog/movie/top/genre=Music.json` → 50 títulos: Whiplash, Coco, La La Land,
  The Muppet Show…). Como `AddonRepository.genres()` armaba la lista solo desde el manifest, el
  género no aparecía nunca. Se agregó `UNLISTED_GENRES = listOf("Music", "Musical")` y la etiqueta
  en español ya existía en `GENRE_ES` ("Música"). Verificado en el KM6: filtro "Género: Música" →
  Whiplash abre con fuentes y Coco con 417 seeds.
- **Lo que NO hay (probado, no supuesto):** videoclips de YouTube. Se midieron Piped e Invidious
  el 2026-10-30: `api.piped.private.coffee` busca bien (19 ítems) pero `/streams/{id}` responde
  **500**; `pipedapi.adminforge.de` 301, `drgns.space` 000, `leptons.xyz` y `lunar.icu` 502,
  `ducks.party` 500; `inv.nadeko.net` 403, `nerdvpn.de` 401, `yewtu.be` devuelve HTML (API
  cerrada). O sea: **hoy no hay forma estable de extraer el video de YouTube** sin API key oficial.
  Si se quiere video-clips de verdad, el camino es YouTube Data API + `yt-dlp`/extractor propio, o
  aceptar que la búsqueda musical sea de películas/conciertos del catálogo.
- **Ojo con el orden de la lista de géneros:** `genres()` devuelve los géneros crudos en inglés y
  la UI los traduce; "Música" queda entre "Terror" y "Musical" en la fila del panel.

## Layout de búsqueda y detalle (2026-10-30, noche)

**La TV es de 960×540 dp, no 1920×1080 px.** Todo el cálculo de espacio tiene que hacerse en dp:
una pantalla de 540 dp de alto se llena con muy poco. De ahí salieron los tres bugs de layout.

1. **Buscador sin resultados.** `ResultsArea` evaluaba `state.filters.isBlank -> Hint(...)` ANTES
   de la rama de ítems. Al abrir el buscador sin filtros (`isBlank == true`) la grilla no se
   dibujaba nunca: el encabezado decía "42 resultados" (porque `statusText` consulta `searched`
   antes que `isBlank`) y abajo quedaba el cartel "Cargando lo más popular…" para siempre. Como no
   había nada focusable debajo, el D-pad "no andaba". Ahora los ítems van primero en el `when`.
2. **Teclado que tapaba todo.** Las teclas eran de 44 dp y el panel medía ~215 dp. Ahora son de
   30 dp (`TextPad`/`KeyCap`) y quedan ~200 dp para la grilla. Los paneles de género/año/nota eran
   `FlowRow` (con 20 géneros crecían a media pantalla): ahora son `LazyRow` de una sola fila, con
   scroll lateral, y arrancan mostrando la opción marcada.
3. **Ficha cultural que se comía el detalle.** Estaba en el alto fijo del encabezado y dejaba
   ~100 dp para las fuentes: se veía una y la segunda cortada. Ahora es el **primer ítem del mismo
   `LazyColumn`** que las fuentes, y el estado de las fuentes se pide en `DetailScreen` (no dentro
   de la lista: anidar `LazyColumn` se mide con altura infinita y recorta).
   - Ojo con el detalle fino: la ficha llega **después** que las fuentes. Al insertarse como ítem 0
     la lista conserva el ancla del foco y la ficha queda por encima del borde visible (el
     "recuadro que se escapa"). Por eso `StreamsList` vuelve al tope cuando aparece la ficha.
   - Segundo detalle fino: cuando las fuentes aparecían y no había nada enfocado, el control remoto
     quedaba muerto hasta apretar una flecha. Ahora la primera tarjeta se enfoca sola (una vez).

## Arranque del video: el inicio del archivo bajaba al final (resuelto en `0eaeef9`)

- **Síntoma medido:** con el torrent al 81 % (7 MB/s, 186 peers) la pantalla seguía negra,
  duración `--:--` y "Audio: esperando pista…". Revisando el archivo en el disco del TV Box
  byte a byte: pct 0 % y 5 % **en ceros**, del 10 % al 95 % con datos. O sea, faltaba justo el
  principio.
- **Prueba contra el servidor local** (por `adb forward`): `Range: bytes=0-1023` mandaba las
  cabeceras 206 y **0 bytes** (colgado 25 s); `Range: bytes=-1024` respondía en 20 ms.
- **Causa:** `prepareDownload` marcaba **todas** las piezas del archivo en `TOP_PRIORITY`. Con
  todas iguales la prioridad no ordena nada y libtorrent reparte por *rarest first*; además nunca
  se usaba `setPieceDeadline`, que es el mecanismo real de libtorrent para streaming.
- **Arreglo:** `StreamPriorities` (nuevo) fuerza una ventana de 8 piezas del inicio en
  `TOP_PRIORITY` **+ deadline 0**, recortada al final del archivo; el archivo elegido pasa a
  `DEFAULT`; la ventana se reafirma cada segundo mientras se espera la primera pieza; y
  `TorrentPieces.prioritize` también pone deadline (sirve para los seeks).
- **Resultado medido:** primer frame a los **26 s** con la caché vacía (antes: más de 5 minutos,
  casi el 100 % de la descarga). A los 26 s el archivo tenía el 0 % descargado (`ftyp` presente)
  y del 5 % al 20 % todavía en ceros: el orden quedó invertido, que es lo correcto.
- **Nota de API:** `libtorrent4j 2.1.0-39` **no** tiene `setSequentialDownload`; lo disponible es
  `setSequentialRange(first, last)`, `piecePriority` y `setPieceDeadline(piece, ms)`.
- **Dato curioso, no es bug:** el mp4 de YTS termina en `0x69` repetido (relleno del release).

## Seguridad del motor de torrents (`TorrentEngineSettings`)

- **Problema:** `SessionManager().start()` sin `settings_pack` heredaba los defaults de libtorrent 1.2:
  `enable_upnp=true`, `enable_natpmp=true`, `enable_lsd=true`, `listen_interfaces=0.0.0.0:6881`,
  `upload_rate_limit=0` (sin tope). El motor abría puertos en el router sin permiso del usuario.
- **Solución:** `core/.../TorrentEngineSettings.kt` (data class con `toSettingsPack()` / `toSessionParams()`)
  y `TorrentEngine` la aplica con `manager.start(settings.toSessionParams())`.
  UPnP/NAT-PMP/LSD apagados; subida con tope (800 KB/s); bajada libre; puerto declarado.
- **Verificación:** `TorrentSessionTest` arranca una sesión real con el nativo de Linux y lee
  `session.settings()` para comprobar que UPnP/NAT-PMP/LSD quedaron en `false`.
  `TorrentEngineSettingsTest` cubre los defaults sin tocar el nativo.
- **Pendiente:** exponer el proxy SOCKS5 (`TorrentEngineSettings.proxy`) en ajustes (M5) y decidir
  si se quiere una opción de "no escuchar" para quien use VPN.

## Ficha cultural (Wikipedia/Wikidata) — `:data/enrich`

- **Por qué APIs y no scraping:** se usan `wbgetentities`, `list=search` y `rest_v1/page/summary`,
  que devuelven JSON y texto plano. Sin HTML, sin WebView, sin JavaScript.
- **Flujo:** IMDb id (`tt…`) → `haswbstatement:P345=` → entidad → P170/P57 (creador/director),
  P161 (reparto), P272 (productora), P495/P364 (país/idioma), P577/P580 (año) → una sola llamada
  de etiquetas → resumen del director (bio) y resumen en español del título. Máximo 5 requests,
  luego cache en memoria (`MAX_CACHED = 48`).
- **Defensas:** allowlist de hosts (`www.wikidata.org`, `es.wikipedia.org`, `en.wikipedia.org`)
  validada también sobre la URL final del redirect; tope de 1 MB por respuesta; timeouts 6/8/12 s;
  User-Agent identificable (política de Wikimedia); `WikiText.clean()` quita etiquetas y entidades,
  colapsa espacios y corta en 420/700 caracteres antes de tocar Compose.
- **Atribución:** el hecho viene de Wikidata (CC0) y la prosa de Wikipedia (CC BY-SA); la UI muestra
  "Fuente: Wikipedia (CC BY-SA)". Si se quiere la URL del artículo en pantalla, está en
  `EnrichedInfo.sourceUrl`.
- **Privacidad (a decidir):** Wikipedia ve qué títulos se consultan desde la IP del TV. Si molesta,
  el siguiente paso es proxyar el enriquecimiento por el homelab (mismo patrón que Radios).

## Bugs y Mejoras Recientes

1. **Barra gris en la parte superior eliminada (Fullscreen / Immersive Mode):**
   - El tema `Theme.TeleTv` usaba `android:Theme.Black` que tiene `windowNoTitle=false` y agrega el DecorView de título nativo de Android (banda gris superior).
   - Solución: se cambió el padre a `android:Theme.Black.NoTitleBar.Fullscreen`, con `windowNoTitle=true` y `windowFullscreen=true`. En `MainActivity.onCreate` se aplicó `Window.FEATURE_NO_TITLE`, flag `FLAG_FULLSCREEN` y `WindowInsetsControllerCompat.hide(systemBars())`.

2. **Limpieza de interfaz del reproductor y control por OK:**
   - Se removió la píldora flotante persistente `▲ Controles (X)` que obstruía la imagen al ocultar controles.
   - Se removió el botón redundante `× Ocultar (X)` de la botonera.
   - Pantalla limpia 100% durante la reproducción.
   - La barra inferior se restaura al presionar la tecla `OK` (o cualquier flecha de D-pad, Menú, o tap/clic en pantalla).
   - Auto-ocultamiento tras 5 segundos de inactividad mientras corre el video.
   - La tecla `Back` con la barra abierta ahora oculta la barra en vez de salirse abruptamente del video.

1. **Los botones de la barra no recibían el clic (mouse).**
   - Evidencia medida: con el build viejo, un tap exacto sobre `Subtítulos` (bounds
     `[731,926][1071,1022]` del dump de accesibilidad) **cerraba** la barra y el label
     seguía en "Español". O sea: disparaba el `clickable` del `Box` raíz y NO el `Card`.
   - Causa acotada: los `androidx.tv.material3.Card(onClick=...)` dentro del subárbol
     `AnimatedVisibility` del footer no tomaban el puntero (la misma `Card` como hija
     directa del `Box`, el botón "Controles", sí funcionaba).
   - Solución: píldoras propias con `Box` + `clickable`, texto oscuro sobre fondo blanco.
   - **Nota:** el `PlayerView` de media3 1.3.1 **no** era el culpable (con
     `use_controller=false` no queda clickable). Se dejó `TouchPassThroughPlayerView`
     como blindaje explícito para que no vuelva a romperse si alguien reactiva el
     controlador nativo.

2. **El control remoto (dpad) no navegaba y OK cerraba la barra.**
   - El `Box` raíz usaba `Modifier.clickable` para mostrar/ocultar: un `clickable` es
     enfocable, y ese foco fantasma se quedaba la navegación (el dump mostraba
     `focused=true` en `[0,50][1920,1080]`).
   - Solución: `pointerInput` + `detectTapGestures` (responde al puntero, no es enfocable)
     + `FocusRequester` que pone el foco en `Reproducir` al abrir la barra.

3. **Pantalla negra: `Ready` nunca llegaba a la UI (el video no arrancaba).**
   - El motor emite `Ready` y **enseguida** (sin pausa previa) empieza a emitir
     `Progress` 1/s. `StateFlow` conflaciona → la UI solo veía `Progress`.
   - Medido: servidor local respondía `206` con 1 KB en 13 ms y cabecera `matroska`
     (vía `adb forward`), mientras la UI mostraba `--:--` y "Audio: esperando pista".
   - Solución: la URL vive en su propio `StateFlow` (`ViewModel.ready`) que se fija una
     vez y no lo pisa el progreso.

4. **Rediseño de la barra** (pedido por Alex): degradado transparente→negro en vez de
   banda gris, píldoras blancas con texto casi negro, foco ámbar con anillo, y se
   quitaron los glifos que el KM6 renderizaba como cuadros (`⛶`, `💬`).
   Se agregó barra de progreso navegable: clic/arrastre con mouse, ±10 s con dpad,
   OK sobre la barra = pausa/reproducir, con tiempo actual/total y buffer.

## Pendiente / en investigación (para la próxima)

- [x] **El video no mostraba imagen** — resuelto en `0eaeef9`, causa medida en el KM6 (ver
      sección "Arranque del video" arriba): el inicio del archivo bajaba al final porque todas
      las piezas estaban en `TOP_PRIORITY` y no se usaba `setPieceDeadline`.
- [ ] Validar seek con mouse y dpad (la duración ya aparece al preparar el medio: 1:47:07).
      Ojo: un seek a una zona **no descargada** ahora espera en el servidor local con deadline,
      así que conviene medir cuánto tarda en vez de asumir que es instantáneo.
- [ ] Selector de subtítulos por idioma con distintos formatos (SRT interno, VTT).
- [ ] Gestión de caché de videos descargados (`context.cacheDir/torrents`), hoy crece sin límite.
      Al desinstalar la app se limpia sola (el `uninstall` borra la caché), pero no hay evicción.
- [ ] **CI:** el `logcat -d` colgado ya está acotado con `-t`, y el smoke pasa. Queda pendiente
      cachear el keystore debug: cada run firma distinto y `adb install -r` sigue fallando con
      `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (hay que desinstalar antes, y eso borra la caché).
- [ ] Nota de tema: la app usa el esquema **claro** por defecto de tv-material3; para una
      app de video probablemente convenga pasar a esquema oscuro (afecta Main/Detail, no
      solo el player).
- [ ] Si la primera pieza no llega en 60 s (`FIRST_PIECE_TIMEOUT_MS`) igual se levanta el
      servidor y la UI sigue diciendo "Cargando el video…". Con la prioridad nueva casi no
      pasa; si un torrent sin seeds se cuelga, falta un estado de error explícito.
- [ ] **Videoclips de música (YouTube):** hoy imposible con Piped/Invidious (ver sección "Música
      en el buscador"). Si Alex los quiere de verdad hay que decidir entre YouTube Data API (key
      propia, cuota diaria) o un extractor local (`yt-dlp` en el homelab sirviendo el archivo por
      HTTP al KM6). Lo segundo se lleva bien con el modelo actual: el player ya reproduce una URL
      HTTP cualquiera.
- [ ] La ficha cultural en **series** sigue sin botón de desplegar (va compacta arriba del
      navegador de temporadas para no robarle alto a los episodios). Si molesta, el mismo patrón
      de `wikiExpanded` se puede aplicar ahí.

## Historial anterior

1. **Crash de inicio (ClassNotFoundException Hilt_TeleTvApp):** se removió `@HiltAndroidApp`
   innecesario (la app usa inyección manual con `AppGraph`).
2. **Crash libtorrent4j en KM6 (UnsatisfiedLinkError):** el firmware corre userland 32-bit
   (`armeabi-v7a`); se agregó `libtorrent4j-android-arm:2.1.0-39`.
3. **Pantalla negra con audio activo:** un `Surface` de Compose ocluía el hardware layer;
   se cambió a `Box` y `PlayerView` a `TextureView`.
4. **Timeout de red:** se subieron los timeouts de `DefaultHttpDataSource` a 120 s.
5. **Conflicto con el controlador nativo:** `app:use_controller="false"`.
