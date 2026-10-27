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
  - Se implementó auto-ocultamiento de controles e información tras 5 segundos de inactividad, permitiendo visualización 100% limpia a pantalla completa, reapareciendo al hacer clic o mover el mouse.
  - Se configuraron timeouts de ExoPlayer a 120s y precarga de la primera pieza en `TorrentEngine`.
  - Se integró `TextureView` y soporte de arquitectura 32-bit (`armeabi-v7a`).
  - Se configuró `PlayerView` para usar `TextureView` (`app:surface_type="texture_view"`), solucionando el problema donde Compose `Surface` bloqueaba u ocluía la capa de hardware de `SurfaceView`.
  - Se reemplazó el `Surface` raíz en `MainActivity` por un `Box` para evitar el buffer offscreen que provocaba pantalla negra con audio.
  - Se añadió botón visible "← Volver" y soporte de `BackHandler` en `PlayerScreen`, permitiendo volver atrás con mouse y control remoto, y deteniendo la reproducción limpia al salir.
  - Se eliminó el plugin y anotación huérfana de Hilt (`@HiltAndroidApp`), que provocaba `ClassNotFoundException: Hilt_TeleTvApp` al iniciar.
  - Se agregó `libtorrent4j-android-arm` a `gradle/libs.versions.toml` y `core/build.gradle.kts` para soporte de TV boxes 32-bit (`armeabi-v7a`).

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
- **Torrents:** LibTorrent4j 2.1.0-39 con binarios nativos C++ JNI
- **CI / Build:** GitHub Actions (Ubuntu latest, JDK 17, Gradle 8.5)
