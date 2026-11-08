# يبني الفرونت ويخدمه عن طريق Caddy (HTTPS تلقائي من Let's Encrypt)
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund && test -x node_modules/.bin/vite
COPY . .
# على السيرفر الـ API على نفس الدومين، فما بدنا ملف _redirects تبع Netlify
RUN rm -f public/_redirects public/api-offline.json && npm run build

FROM caddy:2-alpine
COPY --from=build /app/dist /srv
