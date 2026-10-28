#!/usr/bin/env bash
# Smoke test en emulador: instala el APK, abre la app y falla si se cerró.
#
# Existe porque el runner de `android-emulator-runner` ejecuta el bloque `script`
# LÍNEA POR LÍNEA con `sh`, así que no admite estructuras multilínea (if/fi).
# Por eso la lógica vive acá y el workflow solo hace: bash scripts/smoke-test.sh
set -uo pipefail

PKG="com.tele.app.tv"
APK="${1:-apk/tv-debug.apk}"
WAIT_SECONDS="${2:-25}"

fail() {
  echo "::error::$1"
  echo "===== CRASH BUFFER ====="
  timeout 30 adb logcat -d -b crash -t 800 | tail -150
  echo "===== FATAL ====="
  timeout 30 adb logcat -d -t 4000 | grep -iE "androidruntime|fatal exception|$PKG" | tail -150
  exit 1
}

echo "== esperando al emulador =="
adb wait-for-device
for _ in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then break; fi
  sleep 2
done
echo "boot_completed=$(adb shell getprop sys.boot_completed | tr -d '\r')"

echo "== instalando $APK =="
adb install -r -t "$APK" || fail "No se pudo instalar el APK"

echo "== lanzando $PKG =="
adb shell am start -W -n "$PKG/.ui.MainActivity" || fail "No se pudo lanzar la activity"

echo "== esperando ${WAIT_SECONDS}s =="
sleep "$WAIT_SECONDS"

PID="$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
if [ -z "$PID" ]; then
  fail "La app se cerro al abrir"
fi

echo "OK: la app sigue viva (pid $PID)"
echo "== logcat de la app =="
timeout 30 adb logcat -d -t 4000 | grep -iE "$PKG|TeleTvAudio|TeleTvCrash" | tail -60
exit 0
