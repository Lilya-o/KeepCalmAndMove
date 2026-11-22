#!/usr/bin/env bash
# Lance la démo DESKTOP en une commande : serveur + app.
# Usage : ./demo-desktop.sh   (Ctrl+C pour tout arrêter)
set -euo pipefail
cd "$(dirname "$0")"

echo "══════════════════════════════════════════"
echo "  KeepCalmAndMove — Démo DESKTOP"
echo "══════════════════════════════════════════"

# 1) Serveur : démarrer seulement s'il ne tourne pas déjà sur :8080
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
  if curl -s -o /dev/null http://localhost:8080/disruptions 2>/dev/null; then
    echo "✓ Serveur prêt"
  else
    echo "✗ Le serveur n'a pas démarré. Regarde /tmp/kcm-server.log"; exit 1
  fi
fi

# Nettoyage : si on a lancé le serveur, on le coupe en quittant (Ctrl+C)
cleanup() {
  if [ "$STARTED_SERVER" = "1" ]; then echo ""; echo "⏹ Arrêt du serveur"; kill "${SERVER_PID:-}" 2>/dev/null || true; fi
}
trap cleanup EXIT

# 2) App desktop (au premier plan : la fenêtre s'ouvre)
echo "▶ Lancement de l'app Desktop… (ferme la fenêtre ou Ctrl+C pour arrêter)"
./gradlew :app:desktopApp:run
