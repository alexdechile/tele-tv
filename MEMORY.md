# Tele TV - Memoria y Estado del Proyecto

## Estado Actual de la Sesión (2026-10-26, cierre 22:35)
- **Últimos commits:** `adbfb1c` (video arranca), `6b635bd` (foco del remoto), `8468dfe` (controles clicables + rediseño + seek).
- **APK instalado en el TV Box:** `tv-debug.apk` del Run `40432204045` (commit `adbfb1c`).
- **Estado del TV Box:** MECOOL KM6 (`192.168.1.128:5555`). App detenida al cerrar la sesión.

## Bugs corregidos en esta sesión (verificados en el equipo)

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
