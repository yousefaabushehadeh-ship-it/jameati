import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// الباك إند يشتغل على 8080 ويسمح افتراضيًا بالمصدر http://localhost:3000
// البروكسي يخلي الطلبات من نفس المصدر، فالكوكيز (jameati_access / jameati_refresh) تشتغل بدون مشاكل.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': { target: process.env.VITE_API_TARGET || 'http://localhost:8080', changeOrigin: false },
    },
  },
  preview: { port: 3000 },
});
