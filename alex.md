# Tele TV - Bitácora de Proyecto

## Último Realizado
- **Corrección de pantalla negra en video y control del reproductor:**
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
