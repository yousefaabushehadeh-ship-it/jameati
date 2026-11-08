#!/usr/bin/env bash
# تنصيب جامعتي على سيرفر Ubuntu جديد (Hetzner أو غيره).
#   أول مرة:   sudo bash deploy/hetzner/setup.sh [الدومين]
#   تحديث:     sudo bash deploy/hetzner/setup.sh      (بيسحب آخر كود وبيعيد البناء)
# بدون دومين بيستخدم  <IP>.sslip.io  عشان يشتغل HTTPS فورًا.
set -euo pipefail

cd "$(dirname "$0")/../.."
REPO_DIR="$(pwd)"
ENV_FILE="$REPO_DIR/deploy/hetzner/.env"
COMPOSE=(docker compose -f "$REPO_DIR/deploy/hetzner/docker-compose.yml" --env-file "$ENV_FILE")

if [ "$(id -u)" -ne 0 ]; then echo "شغّله بـ sudo"; exit 1; fi

say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# 1) Docker
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  say "تنصيب Docker"
  if ! curl -fsSL https://get.docker.com | sh; then
    echo "(سكربت Docker الرسمي ما زبط على هالنسخة، بنصّب من مستودعات Ubuntu)"
    apt-get update -y
    apt-get install -y docker.io docker-compose-v2 docker-buildx || apt-get install -y docker.io docker-compose-v2
  fi
  systemctl enable --now docker
fi

# 2) Swap (ضروري على السيرفرات الصغيرة، وبيساعد وقت بناء Java)
MEM_MB=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
SWAP_GB=2; [ "$MEM_MB" -lt 2500 ] && SWAP_GB=4
if ! swapon --show | grep -q .; then
  say "عمل swap بحجم ${SWAP_GB}GB"
  fallocate -l "${SWAP_GB}G" /swapfile && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 3) الجدار الناري
if command -v ufw >/dev/null 2>&1; then
  ufw allow 22/tcp >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443 >/dev/null
  ufw --force enable >/dev/null
fi

# 4) الإعدادات والأسرار (مرة وحدة بس)
FIRST_RUN=false
if [ ! -f "$ENV_FILE" ]; then
  FIRST_RUN=true
  IP="$(curl -fsS4 https://ifconfig.me || hostname -I | awk '{print $1}')"
  DOMAIN="${1:-${IP}.sslip.io}"
  ADMIN_EMAIL=""
  if [ -r /dev/tty ]; then
    read -r -p "إيميل حساب الأدمن (للدخول على صفحة المسودات): " ADMIN_EMAIL < /dev/tty || true
  fi
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)"
  rnd() { openssl rand -hex "$1"; }
  umask 077
  cat > "$ENV_FILE" <<EOF
SITE_DOMAIN=$DOMAIN
DB_PASSWORD=$(rnd 16)
DB_ROOT_PASSWORD=$(rnd 16)
JWT_SECRET=$(rnd 32)
ANALYTICS_HASH_SECRET=$(rnd 32)
INTEGRATION_API_KEY=$(rnd 24)
$(if [ "$MEM_MB" -lt 2500 ]; then
  echo "# سيرفر صغير (${MEM_MB}MB): إعدادات توفير ذاكرة"
  echo "JAVA_OPTS=\"-Xmx448m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1\""
  echo "MYSQL_BUFFER_POOL=64M"
  echo "MYSQL_PERF_SCHEMA=OFF"
fi)
BOOTSTRAP_ADMIN_ENABLED=$([ -n "$ADMIN_EMAIL" ] && echo true || echo false)
BOOTSTRAP_ADMIN_EMAIL=$ADMIN_EMAIL
BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASSWORD
EOF
  umask 022
else
  say "تحديث الكود"
  git -C "$REPO_DIR" pull --ff-only || echo "(ما قدرت أسحب، بكمل بالكود الموجود)"
fi

# 5) البناء والتشغيل
say "بناء وتشغيل (أول مرة بياخد 5 لـ 10 دقايق)"
"${COMPOSE[@]}" up -d --build

# 6) استنى السيرفر يجهز
set -a; . "$ENV_FILE"; set +a
say "استنى الباك إند يشتغل…"
for _ in $(seq 1 90); do
  if "${COMPOSE[@]}" exec -T api curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
    READY=true; break
  fi
  sleep 5
done

echo
if [ "${READY:-false}" = true ]; then
  echo "✅ المنصة شغالة:  https://$SITE_DOMAIN"
else
  echo "⚠️ الباك إند لسا ما جهز. شوف السجل:  docker compose -f deploy/hetzner/docker-compose.yml logs -f api"
fi
echo "🔑 مفتاح Tab (X-API-Key):  $INTEGRATION_API_KEY"
echo "📮 رابط استقبال الفرص:     https://$SITE_DOMAIN/api/v1/integrations/opportunities"
if [ "$FIRST_RUN" = true ] && [ "${BOOTSTRAP_ADMIN_ENABLED}" = true ]; then
  echo "👤 حساب الأدمن:  $BOOTSTRAP_ADMIN_EMAIL"
  echo "   الباسوورد:    $BOOTSTRAP_ADMIN_PASSWORD   (احفظه وغيّره بعد أول دخول)"
fi
echo "(كل الأسرار محفوظة بـ deploy/hetzner/.env على السيرفر)"
