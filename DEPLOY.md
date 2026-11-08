# نشر جامعتي (فرونت + باك إند)

```
frontend/  → Netlify   (React/Vite)
backend/   → Railway   (Spring Boot + MySQL)
```

## 1) الباك إند على Railway
1. New Project → Deploy from GitHub repo → اختار هالريبو.
2. من إعدادات الخدمة: **Root Directory = `backend`** (رح يبني من `Dockerfile` تلقائيًا).
3. داخل نفس المشروع: **+ New → Database → MySQL**.
4. بمتغيرات خدمة الباك إند (Variables) حط:

| المتغير | القيمة |
|---|---|
| `DB_URL` | `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC` |
| `DB_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `DB_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `JWT_SECRET` | نص عشوائي طويل (64 حرف أو أكثر) |
| `ANALYTICS_HASH_SECRET` | نص عشوائي طويل |
| `AUTH_COOKIE_SECURE` | `true` |
| `CORS_ALLOWED_ORIGINS` | `https://jameati-platform.netlify.app` |
| `MAIL_FRONTEND_URL` | `https://jameati-platform.netlify.app` |
| `MPGS_FRONTEND_RETURN_URL` | `https://jameati-platform.netlify.app` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |

5. Settings → Networking → **Generate Domain**. رح يعطيك رابط زي `https://jameati-api.up.railway.app`.

## 2) ربط الفرونت بالباك إند
بملف `frontend/public/_redirects` بدّل سطر `api-offline` بـ:
```
/api/*  https://YOUR-RAILWAY-DOMAIN/api/:splat  200
```

## 3) الفرونت على Netlify
مشروع `jameati-platform` → Project configuration → Build & deploy → Link repository → اختار هالريبو:
- Base directory: `frontend`
- Build command: `npm run build`
- Publish directory: `frontend/dist`

بعدها أي push للريبو بينشر الاثنين تلقائيًا.
