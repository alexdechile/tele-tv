# Tele TV - Memoria y Estado del Proyecto

## Estado Actual de la Sesión (2026-10-30)
- **Últimos commits:** `a72f8ec` (modo inmersivo), `d4da027` (SDH flag), `dd0b0e9` (tecla X/Menu y subtitulos dinamicos).
- **Estado del TV Box:** MECOOL KM6 (`192.168.1.94:5555`). Apagado y sin acceso (Alex está fuera); no se puede instalar ni probar en dispositivo.
- **Pendiente de verificación en TV:** la ficha cultural (Wikidata/Wikipedia) y los ajustes endurecidos del motor solo están probados en CI (tests JVM + nativo de Linux), no en el KM6.

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

- [ ] **El video sigue sin mostrar imagen.** Con `adbfb1c` el estado ya pasa a
      "Cargando el video… · N% · X MB/s" (o sea ExoPlayer **sí** recibió la URL), pero
      se queda buffereando: duración `--:--`, "Audio: esperando pista", pantalla negra
      con el torrent al 25%. Sospecha: ExoPlayer pide el final del MKV (Cues/SeekHead)
      y `HttpStreamServer` bloquea esperando esa pieza. Prueba pendiente: pedir un
      rango al final del archivo por `adb forward` y ver si responde o cuelga.
- [ ] Validar seek con mouse y dpad una vez que haya duración (con el fix debería
      aparecer al preparar el medio).
- [ ] Selector de subtítulos por idioma con distintos formatos (SRT interno, VTT).
- [ ] Gestión de caché de videos descargados (`context.cacheDir/torrents`), hoy crece sin límite.
- [ ] **CI roto (no del código):** el job `smoke` queda colgado para siempre en el paso
      `adb logcat -d` (usar `logcat -d -t N`). Además cada run de CI firma el APK con un
      keystore debug nuevo, así que `adb install -r` falla con
      `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: hay que cachear el keystore o desinstalar antes.
- [ ] Nota de tema: la app usa el esquema **claro** por defecto de tv-material3; para una
      app de video probablemente convenga pasar a esquema oscuro (afecta Main/Detail, no
      solo el player).

## Historial anterior

1. **Crash de inicio (ClassNotFoundException Hilt_TeleTvApp):** se removió `@HiltAndroidApp`
   innecesario (la app usa inyección manual con `AppGraph`).
2. **Crash libtorrent4j en KM6 (UnsatisfiedLinkError):** el firmware corre userland 32-bit
   (`armeabi-v7a`); se agregó `libtorrent4j-android-arm:2.1.0-39`.
3. **Pantalla negra con audio activo:** un `Surface` de Compose ocluía el hardware layer;
   se cambió a `Box` y `PlayerView` a `TextureView`.
4. **Timeout de red:** se subieron los timeouts de `DefaultHttpDataSource` a 120 s.
5. **Conflicto con el controlador nativo:** `app:use_controller="false"`.
