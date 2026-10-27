# Tele TV - Bitácora de Proyecto

## Último Realizado
- **Rediseño completo de controles en Footer persistente:**
  - Se desactivó el controlador nativo de ExoPlayer (`app:use_controller="false"`), impidiendo que `PlayerControlView` intercepte eventos de puntero o bloquee los clics del mouse.
  - Todos los controles de reproducción se unificaron en una barra inferior fija (footer) sobre fondo semitransparente oscuro:
    - `← Volver`: Regresa al catálogo deteniendo la reproducción de forma limpia.
    - `▶ Play / ⏸ Pausa`: Alterna el estado de reproducción de ExoPlayer directamente.
    - `⛶ Pantalla`: Alterna entre ajustar relación original (`FIT`) y pantalla completa (`ZOOM`).
    - `💬 Subtítulos`: Alterna cíclicamente entre Español, Inglés y Desactivados.
    - `✕ Cerrar`: Oculta el footer para visualización de video limpia.
  - Se agregó botón flotante discreto `[ ☰ Controles ]` en la esquina inferior cuando la barra está cerrada, evitando la pérdida de control y permitiendo volver a abrirla con un solo clic de mouse o botón del control.
  - Se eliminó el auto-ocultamiento invasivo por temporizador para asegurar control total por parte del usuario.
- **Modo de pantalla completa y selector de subtítulos (Español/Inglés):**
  - Se agregó selector interactivo de subtítulos en pantalla (`SubtitleChoice`: Español, Inglés, Desactivados) configurando `setPreferredTextLanguage` y `setTrackTypeDisabled` en ExoPlayer.
  - Se agregó botón de alternancia de pantalla completa (`RESIZE_MODE_ZOOM` para llenar 16:9 vs `RESIZE_MODE_FIT` para respetar relación original).
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
- **Conexión ADB:** `adb connect 192.168.1.128:5555`.
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
