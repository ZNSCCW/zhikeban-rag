import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 前端构建配置骨架（ENG-007 任务①）。
 *
 * ★ 本单"可空实现"：只要求 `vite dev` 能起、`vite build` 能编。
 * ★ 开发期 `/api` 反向代理到后端骨架（compose 默认栈里的 backend:8080）。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
