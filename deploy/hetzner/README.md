# نشر جامعتي على Hetzner (سيرفر واحد لكل اشي)

السيرفر الواحد بيشغّل 3 أشياء بـ Docker:
- **db**: MySQL 8.4 (البيانات محفوظة بـ volume)
- **api**: الباك إند (Spring Boot)
- **web**: الموقع (React) عن طريق Caddy، مع شهادة HTTPS تلقائية

الموقع والـ API على نفس الدومين، فما في مشاكل CORS أو كوكيز.

## 1) اعمل السيرفر
بـ Hetzner Cloud → **Add Server**:
- Location: Falkenstein أو Nuremberg أو Helsinki
- Image: **Ubuntu 24.04**
- Type: **CX23** (2 vCPU / 4GB) أو أكبر. بيشتغل كمان على سيرفر **1GB** (السكربت بيعمل swap 4GB وبيخفف إعدادات Java و MySQL لحاله)، بس بيكون أبطأ. بتقدر تكبّره بعدين من **Rescale** بدون ما تخسر البيانات.
- SSH key: ضيف مفتاحك، أو خليه يبعتلك باسوورد root على الإيميل
- خلي IPv4 مفعّل

## 2) ادخل على السيرفر وشغّل سطرين
من PowerShell على جهازك:
```
ssh root@IP-السيرفر
```
بعدين جوا السيرفر:
```
git clone https://github.com/yousefaabushehadeh-ship-it/jameati.git
cd jameati && bash deploy/hetzner/setup.sh
```
رح يسألك عن إيميل الأدمن، وبعد 5 لـ 10 دقايق بيطبعلك:
- رابط الموقع (مثلاً `https://1.2.3.4.sslip.io`)
- مفتاح Tab ورابط استقبال الفرص
- إيميل وباسوورد الأدمن

## دومين خاص (اختياري)
اعمل سجل **A** بالدومين يأشّر على IP السيرفر، وبعدين على السيرفر:
```
cd jameati
sed -i 's/^SITE_DOMAIN=.*/SITE_DOMAIN=app.example.com/' deploy/hetzner/.env
bash deploy/hetzner/setup.sh
```

## تحديث بعد أي تعديل بالكود
السيرفر بيشيك GitHub **كل 10 دقايق** وبينزّل أي تعديل جديد لحاله (السجل: `/var/log/jameati-update.log`).
إذا بدك تحدّث فورًا:
```
cd jameati && bash deploy/hetzner/setup.sh
```

## أوامر مفيدة
```
docker compose -f deploy/hetzner/docker-compose.yml --env-file deploy/hetzner/.env ps
docker compose -f deploy/hetzner/docker-compose.yml --env-file deploy/hetzner/.env logs -f api
```

## نسخة احتياطية لقاعدة البيانات
```
docker compose -f deploy/hetzner/docker-compose.yml --env-file deploy/hetzner/.env exec -T db \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" jameati' | gzip > backup-$(date +%F).sql.gz
```
(أو فعّل **Backups** من لوحة Hetzner، بتزيد 20% على سعر السيرفر.)
