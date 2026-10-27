# Tele TV - Memoria y Estado del Proyecto

## Estado Actual de la Sesión (2026-10-26)
- **Última versión desplegada:** Commit `095901d` + documentación `f5a6f50`.
- **APK instalado en el TV Box:** `tv-debug.apk` generado por GitHub Actions Run `40425685392`.
- **Estado del TV Box:** MECOOL KM6 (`192.168.1.128:5555`) conectado y con `com.tele.app.tv` ejecutándose.

## Historial de Problemas Resueltos en la Sesión
1. **Crash de inicio (ClassNotFoundException Hilt_TeleTvApp):**
   - Se removió `@HiltAndroidApp` innecesario ya que la app usa manual injection singleton (`AppGraph`).
2. **Crash libtorrent4j en KM6 (UnsatisfiedLinkError):**
   - El firmware del KM6 corre userland 32-bit (`armeabi-v7a`). Se agregó `org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39` a Gradle.
3. **Pantalla negra con audio activo:**
   - Compose `Surface` en `MainActivity` ocluía el hardware layer de `SurfaceView`. Se cambió a `Box` y `PlayerView` a `TextureView`.
4. **Timeout de red (ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT):**
   - ExoPlayer fallaba a los 8s esperando el buffer del torrent. Se subió timeout a 120s en `DefaultHttpDataSource` y se aseguró descarga de la primera pieza antes de emitir `Ready`.
5. **Conflicto de eventos de ratón y controles escondidos:**
   - `PlayerControlView` nativo de ExoPlayer interceptaba los clics del mouse y ocultaba los botones de Compose.
   - Solución: `app:use_controller="false"`, diseño de footer inferior persistente con botones (`Volver`, `Play/Pausa`, `Pantalla`, `Subtítulos`, `Cerrar`) y botón flotante `[ ☰ Controles ]` cuando está cerrado.

## Tareas Pendientes para la Próxima Sesión
- [ ] Recibir feedback de Alex sobre el comportamiento del mouse y la visualización del nuevo footer.
- [ ] Validar selector de pistas de subtítulos en diferentes formatos (SRT interno, VTT, etc.).
- [ ] Evaluar barra de progreso / seekbar y porcentaje de buffer del torrent.
- [ ] Limpieza o gestión de caché temporal de videos descargados (`context.cacheDir` o almacenamiento externo).
