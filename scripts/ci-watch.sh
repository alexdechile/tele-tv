#!/usr/bin/env bash
# Espera el resultado del CI de GitHub Actions para el commit actual (HEAD)
# y, si falla, imprime solo las líneas de error del compilador.
#
# Uso:  scripts/ci-watch.sh [segundos_maximos]
# Requiere: curl, python3 y un token de gh en ~/.config/gh/hosts.yml
set -uo pipefail

REPO="${REPO:-alexdechile/tele-tv}"
MAX_WAIT="${1:-600}"
GH_HOSTS="${GH_HOSTS:-$HOME/.config/gh/hosts.yml}"

TOKEN=$(awk '/oauth_token:/{print $2; exit}' "$GH_HOSTS" 2>/dev/null)
if [ -z "${TOKEN:-}" ]; then
  echo "ERROR: no hay oauth_token en $GH_HOSTS" >&2
  exit 1
fi

SHA=$(git rev-parse HEAD)
API="https://api.github.com/repos/$REPO"

echo "Commit: ${SHA:0:7} ($REPO)"

run_id=""
waited=0
while [ "$waited" -lt "$MAX_WAIT" ]; do
  run_id=$(curl -s -H "Authorization: Bearer $TOKEN" \
    "$API/actions/runs?per_page=20" |
    SHA="$SHA" python3 -c '
import json, os, sys
sha = os.environ["SHA"]
data = json.load(sys.stdin)
for run in data.get("workflow_runs", []):
    if run.get("head_sha") == sha:
        print(run["id"])
        break
')
  [ -n "$run_id" ] && break
  sleep 10
  waited=$((waited + 10))
done

if [ -z "$run_id" ]; then
  echo "No apareció un run para este commit en ${MAX_WAIT}s."
  exit 2
fi

echo "Run id: $run_id"
while [ "$waited" -lt "$MAX_WAIT" ]; do
  read -r status conclusion < <(curl -s -H "Authorization: Bearer $TOKEN" "$API/actions/runs/$run_id" |
    python3 -c 'import json,sys; r=json.load(sys.stdin); print(r["status"], r["conclusion"])')
  echo "  [$((waited))s] $status/${conclusion:-…}"
  if [ "$status" = "completed" ]; then break; fi
  sleep 15
  waited=$((waited + 15))
done

if [ "$conclusion" = "success" ]; then
  echo "✅ CI OK"
  exit 0
fi

echo "❌ CI: ${conclusion:-timeout}"
job_id=$(curl -s -H "Authorization: Bearer $TOKEN" "$API/actions/runs/$run_id/jobs" |
  python3 -c 'import json,sys; print(json.load(sys.stdin)["jobs"][0]["id"])')
curl -sL -H "Authorization: Bearer $TOKEN" "$API/actions/jobs/$job_id/logs" -o /tmp/ci-last.log
echo "--- errores ---"
grep -E "^.*e: |FAILURE: Build|error:" /tmp/ci-last.log | head -40
echo "(log completo: /tmp/ci-last.log)"
exit 1
