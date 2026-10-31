# Tele TV - Bitácora de Proyecto

## Último Realizado
- **Pantalla de búsqueda y de detalle arregladas (probadas en el TV Box):**
  - **Buscador:** los resultados ahora se ven aunque el teclado esté abierto y se bajan con el D-pad. Antes la lista de resultados no se dibujaba nunca si no había filtros (el encabezado decía "42 resultados" y abajo no había nada), el teclado ocupaba media pantalla y el control remoto no tenía a dónde ir.
  - El teclado es más chico, los paneles de género/año/nota ahora son una sola fila que se corre de costado (antes se envolvían y crecían a media pantalla) y las tarjetas de resultado son más chicas, así que siempre queda una fila entera a la vista mientras se escribe.
  - **Detalle:** la ficha cultural ya no se queda con el alto fijo de la pantalla. Ahora es el primer ítem de la lista de fuentes: se ve al entrar y se va para arriba cuando bajás a las fuentes, que es lo que uno quiere tocar.
  - Antes se veía una sola fuente y la segunda cortada en el borde (un recuadro que "se escapaba" de la pantalla). Ahora se ven tres o cuatro y todas se alcanzan con el D-pad.
  - En las series la ficha va compacta (una línea de biografía) arriba del selector de temporadas y episodios.
  - **También se arregló:** al abrir una película a veces no quedaba nada enfocado y el control remoto "no hacía nada" hasta apretar una flecha. Ahora la primera fuente se enfoca sola.
- **El video arranca en segundos, no al final de la descarga (probado en el TV Box):**
  - Antes: la película se veía recién cuando el torrent iba casi al 100 %. Medido: con 81 % descargado la pantalla seguía negra y el archivo todavía **no tenía el principio** (los primeros ~8 % estaban vacíos en el disco del KM6).
  - Causa: el motor marcaba todas las piezas del archivo con la misma prioridad máxima, así que libtorrent las repartía "como vinieran", y no usaba el plazo (*deadline*) que libtorrent tiene justamente para streaming.
  - Ahora: el inicio del archivo tiene prioridad máxima **y plazo**, y se vuelve a pedir cada segundo hasta que llega. El resto queda en prioridad normal.
  - Resultado medido en el KM6 con la caché vacía: primer cuadro a los **26 segundos** (antes: más de 5 minutos). Verificado además que a los 26 s el archivo tenía el principio y el resto vacío: el orden quedó al revés de como estaba, que es lo correcto.
  - Un seek a una parte no descargada también se pide con plazo, así que ya no se queda colgado esperando piezas lejanas.
- **Script para no perder el TV Box:** `scripts/tvbox.sh` busca el equipo en la red (el KM6 tiene Wi-Fi y cable a la vez, con IP distinta en cada uno) y confirma que sea el modelo correcto antes de conectar.
- **Ficha cultural en la pantalla de detalle (Wikidata + Wikipedia):**
  - Al abrir una película o serie, debajo de la sinopsis aparece una ficha con quién la dirigió (o quién la creó, en el caso de las series), una biografía corta del director, el reparto principal, la productora, el país, el idioma y el año.
  - Los datos salen de las APIs oficiales de Wikidata y Wikipedia (texto plano en español), no de scraping de HTML.
  - La ficha se muestra con la atribución "Fuente: Wikipedia (CC BY-SA)" y solo agrega texto: la navegación con el control remoto no cambia.
  - Si Wikidata no tiene el título o internet falla, la pantalla queda exactamente como antes, con los datos de siempre; la ficha es opcional y nunca bloquea.
- **Seguridad del motor de torrents (ajustes explícitos):**
  - La app ya no le pide al router que abra puertos por su cuenta (UPnP y NAT-PMP apagados).
  - Tampoco anuncia en la red local lo que se está bajando (descubrimiento local apagado).
  - La subida tiene un tope para no comerse el ancho de banda del video, y el puerto de escucha queda declarado de forma explícita.
  - Hay pruebas automáticas que fallan si alguien vuelve a encender esos interruptores.
- **Eliminación de la barra gris superior (Modo Inmersivo / Fullscreen Real):**
  - Se configuró el tema `Theme.TeleTv` con `parent="android:Theme.Black.NoTitleBar.Fullscreen"`, `android:windowNoTitle=true` y `android:windowFullscreen=true` en `themes.xml`.
  - En `MainActivity.onCreate`, se desactivó la barra de título nativa (`requestWindowFeature(Window.FEATURE_NO_TITLE)`), se fijó flag `FLAG_FULLSCREEN` y se configuró `WindowCompat` con `WindowInsetsControllerCompat.hide(systemBars())` y `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- **Limpieza total del reproductor y control por OK del remoto:**
  - Se eliminó completamente la píldora flotante obtrusiva (`▲ Controles (X)`) y el botón `× Ocultar (X)` de la barra.
  - Al estar oculta la barra, el video se reproduce 100% limpio a pantalla completa sin elementos invasivos.
  - Presionar la tecla `OK` (o `D-pad Center`, `Enter`, `D-pad Up/Down`, `Menú`, o clic en pantalla) restaura la botonera enfocando de inmediato el botón de reproducción/pausa.
  - Se agregó auto-ocultamiento inteligente tras 5 segundos de inactividad mientras el video se reproduce (se mantiene abierta si está en pausa).
  - Presionar `Back` con la barra abierta ahora oculta la barra en vez de salirse del video (un segundo `Back` regresa a la ficha).
- **Atajos de control remoto para la botonera (Tecla `X` y `Menu`):**
  - Se agregó escucha global de teclas en `PlayerScreen` (`onPreviewKeyEvent`).
  - Presionar la tecla `X` (en teclado o air-mouse) o la tecla `Menu` (en controles de TV Box) alterna la visibilidad de la botonera (`controlsOpen`).
  - Al estar oculta la barra, presionar `D-pad Down`, `D-pad Up`, `X`, `Menu` o `Enter` restaura la botonera y enfoca de inmediato el botón de reproducción/pausa.
  - Al ocultarse la barra, el foco se transfiere automáticamente a la píldora discreta `▲ Controles (X)` para que el control remoto nunca quede con foco perdido.
- **Manejo dinámico y real de subtítulos (ExoPlayer `Tracks`):**
  - Se reemplazó el selector estático a ciegas por inspección de pistas reales en `onTracksChanged(tracks: Tracks)`.
  - Mapeo inteligente de códigos ISO (`spa`, `es`, `es-419`, `es-ES`, `eng`, etc.) y detección de subtítulos forzados (`isForced`) o para personas sordas (`SDH`).
  - Selección exacta mediante `TrackSelectionOverride` en ExoPlayer, evitando que el reproductor ignore pistas marcadas como `es` o `und`.
  - Selección automática inteligente al iniciar el video: busca la mejor pista en español no forzada por defecto.
  - El botón muestra el nombre real de la pista (`"Subtítulos: Español (Latino)"`, `"Subtítulos: Desactivados"` o `"Subtítulos: No disponibles"`) y cicla limpiamente entre las pistas existentes en el archivo.
- **Buscador avanzado y soporte de episodios (previo):**
  - Corrección del protocolo Stremio v3 en `StremioUrls` para evitar el escape del segmento de extras (`search`, `genre`).
  - Búsqueda en dos etapas: catálogo rápido + enriquecimiento progresivo en segundo plano.
  - Selección de fuentes por episodio individual (`tt...:S:E`).
- **Resolución de pantalla negra (Video Surface) y Decoder AAC / Network Timeout:**
  - Se sustituyó `SurfaceView` por `TextureView` en `tele_player_view.xml` y se cambió el `Surface` raíz en `MainActivity` por `Box`, solucionando la oclusión que producía audio sin imagen.
  - Se aumentaron los timeouts de `DefaultHttpDataSource` de 8s a 120s en `PlayerScreen.kt`.
  - Se ajustó `TorrentEngine.kt` para priorizar `firstPiece` y `lastPiece` y esperar la primera pieza antes de emitir estado `Ready`.
- **Compatibilidad con arquitectura 32-bit (MECOOL KM6):**
  - Se agregó `libtorrent4j-android-arm:2.1.0-39` a `gradle/libs.versions.toml` y `core/build.gradle.kts` para soportar `armeabi-v7a`.
  - Se removió `@HiltAndroidApp` huérfano que generaba crash en arranque (`ClassNotFoundException: Hilt_TeleTvApp`).

## Dónde íbamos / Próximos Pasos al Volver
1. **Validación de la interfaz con Alex:**
   - Probar la respuesta del nuevo footer inferior con el mouse físico y con el control remoto del TV Box.
   - Verificar si los subtítulos embebidos en el MKV/MP4 se seleccionan adecuadamente al pulsar el botón `💬 Subtítulos`.
2. **Buffer y barra de progreso:**
   - Evaluar agregar una barra de progreso / seekbar visual en el footer para ver el avance y porcentaje de descarga del torrent.
3. **Manejo de errores amigable:**
   - Si un torrent no tiene seeds o demora demasiado, mostrar mensaje de espera con opción de cancelar o elegir otro stream.

## Dispositivo TV Box & Entorno Homelab
- **Dispositivo:** MECOOL KM6 (Amlogic S905X4, Android TV 10 / 11).
- **Arquitectura userland:** 32-bit (`armeabi-v7a`).
- **Conexión ADB:** `adb connect 192.168.1.94:5555`.
- **Regla de compilación:** Nube primero (GitHub Actions). No compilar en servidor local.

## ¿Qué hace esta App?
Tele TV es una aplicación nativa para Android TV enfocada en la reproducción y streaming en tiempo real de video vía torrents:
- Interfaz 10-foot UI optimizada para control remoto D-pad con Jetpack Compose for TV.
- Catálogo y metadatos de películas y series mediante el protocolo de Addons de Stremio v3 (Cinemeta y Torrentio).
- Motor de streaming basado en `libtorrent4j` que descarga secuencialmente y expone un servidor HTTP local para servir el contenido como stream continuo.
- Reproductor de video basado en AndroidX Media3 / ExoPlayer con soporte de passthrough de audio HDMI (AC3 / E-AC3 / DTS) para chips Amlogic.

## Stack Técnico
- **Lenguaje:** Kotlin 2.0.20
- **UI:** Jetpack Compose for TV (`androidx.tv.material3`)
- **Arquitectura:** Multimódulo (`:core`, `:data`, `:tv`), Single-Activity, ViewModels con Coroutines y StateFlow.
- **Reproducción:** Media3 / ExoPlayer 1.3.1 con `AudioAwareRenderersFactory`
- **Red y Catálogo:** Retrofit 2.11 + OkHttp 4.12 + Gson (Protocolo Stremio v3)
- **Torrents:** LibTorrent4j 2.1.0-39 con binarios nativos C++ JNI (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
- **CI / Build:** GitHub Actions (Ubuntu latest, JDK 17, Gradle 8.5)
