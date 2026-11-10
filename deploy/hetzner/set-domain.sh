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
for host in "$DOMAIN" "www.$DOMAIN"; do
  got="$(getent ahostsv4 "$host" | awk 'NR==1{print $1}')"
  if [ "$got" = "$IP" ]; then echo "✅ $host ← $got"; else echo "⚠️  $host ← ${got:-ما في سجل} (لازم يكون $IP)"; fi
done

sed -i '/^SITE_DOMAIN=/d;/^WWW_DOMAIN=/d' "$ENV_FILE"
echo "SITE_DOMAIN=$DOMAIN" >> "$ENV_FILE"
echo "WWW_DOMAIN=www.$DOMAIN" >> "$ENV_FILE"

docker compose -f deploy/hetzner/docker-compose.yml --env-file "$ENV_FILE" up -d --build
echo
echo "تم. الموقع رح يشتغل على https://$DOMAIN"
echo "(شهادة HTTPS بتطلع لحالها خلال دقيقة أو دقيقتين بعد ما يوصل الـ DNS. إذا لسا مش واصل، Caddy بيضل يحاول.)"
echo "رابط Tab الجديد: https://$DOMAIN/api/v1/integrations/opportunities"
