#!/usr/bin/env bash
# بيشتغل كل 10 دقايق (cron): إذا في كود جديد على GitHub بيسحبه وبيعيد البناء.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec 9>/tmp/jameati-update.lock
flock -n 9 || exit 0
git fetch -q origin main
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "$(date -Is) تحديث لـ $(git rev-parse --short origin/main)"
  git merge -q --ff-only origin/main
  docker compose -f deploy/hetzner/docker-compose.yml --env-file deploy/hetzner/.env up -d --build
  docker image prune -f >/dev/null
fi
