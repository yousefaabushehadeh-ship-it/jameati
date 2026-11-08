# جامعتي — الفرونت إند

واجهة React (Vite) مربوطة على الباك إند (Spring Boot) الموجود بمجلد `../backend`.

## التشغيل

1. شغّل الباك إند أولًا على المنفذ 8080 (بحتاج MySQL):
   ```
   cd ../backend
   mvn spring-boot:run
   ```
2. شغّل الفرونت:
   ```
   npm install
   npm run dev
   ```
3. افتح http://localhost:3000

الفرونت بيشتغل على المنفذ 3000 لأنه المصدر المسموح افتراضيًا بالباك إند (`CORS_ALLOWED_ORIGINS`).
طلبات `/api` بتتحول تلقائيًا للباك إند عن طريق البروكسي بـ `vite.config.js`، فكوكيز تسجيل الدخول بتشتغل بدون إعدادات إضافية.
لو الباك إند على عنوان ثاني: `VITE_API_TARGET=http://host:port npm run dev`

## الصفحات

- `/` الرئيسية: بحث، فئات، فرص مميزة، جامعات
- `/opportunities` و `/opportunities/:slug` الفرص والمنح مع فلاتر، مفضلة، متابعة الطلب، ومساعد AI (لو مفعّل)
- `/catalog/UNIVERSITY|SPECIALITY|JOB|COURSE` والتفاصيل
- `/login` `/register` `/forgot-password` `/reset-password` `/verify-email`
- `/me` مساحتي: نظرة عامة، طلباتي، المفضلة، الإشعارات، الملف الشخصي
- `/membership` باقات العضوية والدفع عبر CliQ مع رفع الإيصال
- `/contact` تواصل معنا

## للإنتاج

`npm run build` بيطلع الملفات بمجلد `dist/`. لازم السيرفر اللي بيخدمها يحوّل `/api` للباك إند (مثلاً nginx) ويرجّع `index.html` لأي مسار ثاني.
