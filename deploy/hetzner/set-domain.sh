#!/usr/bin/env bash
# ربط الموقع بدومين خاص:   bash deploy/hetzner/set-domain.sh example.com
# قبلها لازم يكون سجل A للدومين (و www) مأشّر على IP السيرفر.
set -euo pipefail
cd "$(dirname "$0")/../.."
DOMAIN="${1:-}"
DOMAIN="${DOMAIN#http://}"; DOMAIN="${DOMAIN#https://}"; DOMAIN="${DOMAIN%%/*}"; DOMAIN="${DOMAIN#www.}"
[ -n "$DOMAIN" ] || { echo "اكتب الدومين:  bash deploy/hetzner/set-domain.sh example.com"; exit 1; }
ENV_FILE=deploy/hetzner/.env
[ -f "$ENV_FILE" ] || { echo "شغّل setup.sh أول"; exit 1; }

IP="$(curl -fsS4 https://ifconfig.me || true)"
echo "IP السيرفر: $IP"
# www بس للدومين الأساسي (example.com)، مش للفرعي (new.example.com)
WWW=""; [ "$(echo "$DOMAIN" | tr -cd '.' | wc -c)" -eq 1 ] && WWW="www.$DOMAIN"
for host in "$DOMAIN" $WWW; do
  got="$(getent ahostsv4 "$host" | awk 'NR==1{print $1}')"
  if [ "$got" = "$IP" ]; then echo "✅ $host ← $got"; else echo "⚠️  $host ← ${got:-ما في سجل} (لازم يكون $IP)"; fi
done

sed -i '/^SITE_DOMAIN=/d;/^WWW_DOMAIN=/d' "$ENV_FILE"
echo "SITE_DOMAIN=$DOMAIN" >> "$ENV_FILE"
[ -n "$WWW" ] && echo "WWW_DOMAIN=$WWW" >> "$ENV_FILE"

docker compose -f deploy/hetzner/docker-compose.yml --env-file "$ENV_FILE" up -d --build

# تحديث تلقائي من GitHub كل 10 دقايق (عشان ما تحتاج تدخل السيرفر مرة ثانية)
command -v cron >/dev/null 2>&1 || { apt-get install -y cron >/dev/null && systemctl enable --now cron; }
echo "*/10 * * * * root bash $(pwd)/deploy/hetzner/auto-update.sh >> /var/log/jameati-update.log 2>&1" > /etc/cron.d/jameati-update
chmod 644 /etc/cron.d/jameati-update
echo
echo "تم. الموقع رح يشتغل على https://$DOMAIN"
echo "(شهادة HTTPS بتطلع لحالها خلال دقيقة أو دقيقتين بعد ما يوصل الـ DNS. إذا لسا مش واصل، Caddy بيضل يحاول.)"
echo "رابط Tab الجديد: https://$DOMAIN/api/v1/integrations/opportunities"
