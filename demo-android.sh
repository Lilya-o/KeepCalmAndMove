#!/usr/bin/env bash
# Lance la démo ANDROID en une commande : serveur + émulateur + tunnel + app.
# Usage : ./demo-android.sh   (Ctrl+C pour arrêter le serveur ; l'émulateur reste ouvert)
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"

echo "══════════════════════════════════════════"
echo "  KeepCalmAndMove — Démo ANDROID"
echo "══════════════════════════════════════════"
[ -x "$ADB" ] || { echo "✗ adb introuvable à $ADB (installe le SDK ou exporte ANDROID_HOME)"; exit 1; }

# 1) Serveur
STARTED_SERVER=0
if curl -s -o /dev/null http://localhost:8080/disruptions 2>/dev/null; then
  echo "✓ Serveur déjà en marche sur :8080"
else
  echo "▶ Démarrage du serveur… (logs: /tmp/kcm-server.log)"
  ./gradlew -q :server:run > /tmp/kcm-server.log 2>&1 &
  SERVER_PID=$!
  STARTED_SERVER=1
  for _ in $(seq 1 90); do
    if curl -s -o /dev/null http://localhost:8080/disruptions 2>/dev/null; then break; fi
    sleep 1
  done
  curl -s -o /dev/null http://localhost:8080/disruptions 2>/dev/null \
    && echo "✓ Serveur prêt" || { echo "✗ Serveur KO, voir /tmp/kcm-server.log"; exit 1; }
fi
cleanup() {
  if [ "$STARTED_SERVER" = "1" ]; then echo ""; echo "⏹ Arrêt du serveur"; kill "${SERVER_PID:-}" 2>/dev/null || true; fi
}
trap cleanup EXIT

# 2) Émulateur : le démarrer si aucun appareil n'est connecté
if "$ADB" devices | grep -qw "device"; then
  echo "✓ Un appareil/émulateur est déjà connecté"
else
  AVD=$("$EMU" -list-avds 2>/dev/null | head -1 || true)
  [ -n "$AVD" ] || { echo "✗ Aucun AVD. Crée un émulateur dans Android Studio (Device Manager)."; exit 1; }
  echo "▶ Démarrage de l'émulateur : $AVD… (logs: /tmp/kcm-emulator.log)"
  "$EMU" -avd "$AVD" > /tmp/kcm-emulator.log 2>&1 &
fi

echo "⏳ Attente du démarrage complet d'Android…"
"$ADB" wait-for-device
for _ in $(seq 1 120); do
  if [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then break; fi
  sleep 2
done
echo "✓ Android prêt"

# 3) Tunnel réseau (indispensable : relie localhost:8080 du téléphone au serveur)
"$ADB" reverse tcp:8080 tcp:8080
echo "✓ Tunnel adb reverse 8080 actif"

# 4) Build + install + lancement
echo "▶ Installation de l'app… (première fois : un peu long)"
./gradlew -q :app:androidApp:installDebug
"$ADB" shell am start -n lauzhack.sbb.winners/.MainActivity >/dev/null
echo ""
echo "✅ App lancée sur l'émulateur."
echo "   Ctrl+C ici = arrêter le serveur (l'émulateur, lui, reste ouvert)."

# Garder le serveur vivant au premier plan pour que Ctrl+C le coupe proprement
if [ "$STARTED_SERVER" = "1" ]; then wait "$SERVER_PID"; fi
