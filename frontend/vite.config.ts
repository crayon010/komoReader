import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      // 本地开发时把 /api 转发到 Java 后端（默认 Spring Boot 端口 8080）
      '/api': {
        target: 'http://localhost:1236', // 后端服务地址
        changeOrigin: true, // 开启跨域，模拟后端同源
        // rewrite: (path) => path.replace(/^\/api/, ''), // 把请求路径里的 /api 删掉
      },
    },
  },
})
