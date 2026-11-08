# نشر جامعتي (فرونت + باك إند)

> **سيرفر مدفوع (Hetzner)؟** كل اشي على سيرفر واحد: شوف [deploy/hetzner/README.md](deploy/hetzner/README.md).

```
frontend/  → Netlify   (React/Vite)
backend/   → Render (مجاني) + MySQL على Aiven (مجاني)
             (أو Railway — مدفوع وأقوى، الخطوات بالأسفل)
```

## 1-أ) الباك إند على Render + Aiven (مجاني)
1. Aiven: أنشئ خدمة MySQL بخطة Free، وخذ Host / Port / User / Password.
2. Render: New → Blueprint → اختار هالريبو (بيقرأ `render.yaml`).
3. عبّي:
   - `DB_URL` = `jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`
   - `DB_USERNAME` / `DB_PASSWORD` من Aiven
4. بعد النشر رح يعطيك رابط زي `https://jameati-api.onrender.com`.

ملاحظة: الخطة المجانية بتنام بعد ~15 دقيقة بدون زيارات، وأول طلب بعدها بياخذ حوالي دقيقة.

## 1-ب) بديل: الباك إند على Railway
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

---

## استقبال الفرص من واتساب (Tab)

السيرفر فيه رابط بيستقبل الفرص كـ JSON وبيحفظها **مسودات** (مش منشورة) لحد ما الأدمن يوافق.

- الرابط: `POST https://<render-domain>/api/v1/integrations/opportunities`
- الهيدر: `X-API-Key: <قيمة INTEGRATION_API_KEY من Render → Environment>`
- `Content-Type: application/json`

مثال (فرصة وحدة، أو مصفوفة لحد 50 فرصة):

```json
{
  "title": "منحة جامعة X لدراسة الماجستير",
  "organization": "جامعة X",
  "deadline": "2027-01-15",
  "link": "https://example.org/apply",
  "type": "scholarship",
  "funding": "FULLY_FUNDED",
  "country": "ألمانيا",
  "description": "نص المنشور أو ملخصه",
  "source": "اسم جروب الواتساب"
}
```

- المطلوب بس `title`. الباقي اختياري.
- `type` بيحدد القسم:
  - **الفرص**: scholarship / internship / training / volunteering / fellowship / exchange / conference / competition
  - **الوظائف**: job
  - **الدورات**: course / workshop
  (بالعربي كمان: منحة، تدريب، تطوع، زمالة، مؤتمر، مسابقة، وظيفة، دورة، ورشة)
- `category` اختياري: تصنيف داخل القسم (مثلاً "تقنية وبرمجة" للدورات).
- `funding`: FULLY_FUNDED / PARTIALLY_FUNDED / SELF_FUNDED / FREE / PAID (أو "ممولة بالكامل"…).
- `deadline` بصيغة `YYYY-MM-DD`؛ لو نص حر بينحفظ كنص.
- التكرار: نفس الرابط، أو نفس العنوان + الجهة ⇒ `duplicate` وما بتنضاف. اللي حذفها الأدمن ما بترجع.

الرد: `{ "data": { "created": 1, "duplicates": 0, "rejected": 0, "results": [ { "status": "created", "id": 12, ... } ] } }`

### الموافقة على المسودات
1. أنشئ حساب أدمن: بـ Render → Environment ضيف
   `BOOTSTRAP_ADMIN_ENABLED=true` و `BOOTSTRAP_ADMIN_EMAIL=...` و `BOOTSTRAP_ADMIN_PASSWORD=...` (12 حرف أو أكثر)،
   بإيميل **ما انسجّل فيه قبل**. (لو فعّلت ENABLED بدون الإيميل والباسوورد السيرفر ما بيشتغل.)
2. سجّل دخول بالموقع بهالحساب، رح يطلعلك زر **المسودات** فوق (أو افتح `/admin/drafts`).
3. لكل منشور (فرصة/وظيفة/دورة): عدّل التصنيف (والتمويل للفرص) إذا بدك، واضغط **نشر** أو **حذف**. بينعرض بقسمه بالموقع.
