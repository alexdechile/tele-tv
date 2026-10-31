#!/usr/bin/env bash
# Encuentra el TV Box (MECOOL KM6) en la red local y deja la conexión ADB lista.
#
# El KM6 toma IP por DHCP y tiene dos interfaces activas a la vez (cable y
# wifi), así que la IP que funcionó ayer puede no ser la de hoy. Este script no
# adivina: escanea la subred buscando el puerto 5555 (ADB por red) y confirma
# que el dispositivo es realmente el KM6 antes de decir que está listo.
#
# Uso:
#   scripts/tvbox.sh              # busca, conecta e imprime la IP
#   scripts/tvbox.sh --quiet      # imprime solo la IP (para usar en scripts)
#
# Salida: 0 si quedó conectado, 1 si no se encontró.
set -uo pipefail

QUIET=0
[ "${1:-}" = "--quiet" ] && QUIET=1

log() { [ "$QUIET" -eq 1 ] || echo "$*"; }

# Subred: se deduce de la ruta por defecto (funciona en cualquier red).
IFACE=$(ip route | awk '/^default/{print $5; exit}')
BASE=$(ip -4 addr show "$IFACE" 2>/dev/null | awk '/inet /{print $2}' | cut -d/ -f1 | cut -d. -f1-3)
if [ -z "${BASE:-}" ]; then
  echo "ERROR: no se pudo deducir la subred desde $IFACE" >&2
  exit 1
fi
log "Buscando el TV Box en ${BASE}.0/24 (interfaz $IFACE)…"

# Candidatas: lo que ya conoce la tabla ARP primero (es instantáneo y evita
# escanear 254 hosts), después el barrido completo por si cambió de IP.
CANDIDATAS=$(ip neigh 2>/dev/null | awk -v p="$BASE." '$1 ~ "^"p {print $1}')
FOUND=""

probar() {
  local ip="$1"
  # Puerto 5555 abierto = adbd escuchando en la red.
  timeout 1 bash -c "echo > /dev/tcp/$ip/5555" 2>/dev/null || return 1
  adb connect "$ip:5555" >/dev/null 2>&1
  local modelo
  modelo=$(timeout 5 adb -s "$ip:5555" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
  [ "$modelo" = "KM6" ] || return 1
  echo "$ip"
  return 0
}

for ip in $CANDIDATAS; do
  [ "$ip" = "${BASE}.1" ] && continue   # el router no es
  r=$(probar "$ip") && FOUND="$r" && break
done

if [ -z "$FOUND" ]; then
  log "No estaba en la tabla ARP; barriendo ${BASE}.1-254…"
  for n in $(seq 2 254); do
    ip="${BASE}.${n}"
    echo "$CANDIDATAS" | grep -qx "$ip" && continue
    ( timeout 0.4 bash -c "echo > /dev/tcp/$ip/5555" 2>/dev/null && echo "$ip" ) &
  done > /tmp/tvbox-scan.$$ 2>/dev/null
  wait
  for ip in $(sort -u /tmp/tvbox-scan.$$); do
    r=$(probar "$ip") && FOUND="$r" && break
  done
  rm -f /tmp/tvbox-scan.$$
fi

if [ -z "$FOUND" ]; then
  echo "ERROR: no se encontró el TV Box. ¿Está encendido y con ADB por red activo?" >&2
  exit 1
fi

log "TV Box encontrado: $FOUND:5555 (KM6)"
BASE_IP=$(echo "$FOUND" | cut -d. -f1-3)

if [ "$QUIET" -eq 1 ]; then
  echo "$FOUND"
else
  echo
  echo "  adb connect $FOUND:5555"
  echo
  adb devices | awk -v ip="$FOUND:5555" 'NR==1 || $1==ip'
  echo
  echo "Sugerencia para que no se caiga: reserva esa IP (${FOUND}) para el MAC"
  echo "del KM6 en el router, o fíjala en Ajustes → Red del TV Box."
fi
