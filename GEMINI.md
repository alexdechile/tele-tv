# Tele TV - Directrices del Proyecto y Homelab

## Resumen del Proyecto
Tele TV es una app de Android TV para ver películas y series por streaming P2P directo (protocolo Stremio v3 + Torrentio + LibTorrent4j + ExoPlayer).

## Reglas Críticas del Entorno

1. **Compilación en la Nube (Cloud-First):**
   - **NUNCA ejecutar `./gradlew assembleDebug` en la máquina local.**
   - Flujo de compilación y despliegue:
     1. `git commit -am "..." && git push origin main`
     2. GitHub Actions compila el APK debug en `.github/workflows/ci.yml`.
     3. Descargar artefacto: `gh run download <run_id> -n tele-tv-debug -D /tmp/tele-bin` o vía `gh api /repos/alexdechile/tele-tv/actions/artifacts/<id>/zip`.
     4. Instalar en TV Box: `adb uninstall com.tele.app.tv && adb install /tmp/tele-bin/tv-debug.apk`.
     5. Lanzar: `adb shell am start -n com.tele.app.tv/.ui.MainActivity`.

2. **Hardware del TV Box (MECOOL KM6):**
   - **IP ADB:** `192.168.1.94:5555` (conectar vía `adb connect 192.168.1.94:5555`).
   - **CPU:** Amlogic S905X4.
   - **Userland ABI:** 32-bit (`armeabi-v7a`). Cualquier librería nativa C/C++ (`.so`) DEBE incluir soporte `armeabi-v7a` (`libtorrent4j-android-arm`).

3. **Arquitectura del Reproductor y Compose:**
   - La ventana de Compose no debe usar `Surface` opaca en la raíz de `MainActivity`, sino `Box`.
   - `PlayerView` usa `app:surface_type="texture_view"` y `app:use_controller="false"` para permitir que Jetpack Compose controle toda la interfaz sin capturas de mouse espurias.

4. **Documentación:**
   - Mantener siempre actualizado `alex.md` (bitácora principal con "Último Realizado" arriba) y `MEMORY.md`.
