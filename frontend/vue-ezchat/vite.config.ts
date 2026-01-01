import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    // 允许你的外部域名访问
    allowedHosts: [
      'ez-chat.vornet.i234.me'
    ],
    // 关键：告诉 Vite 浏览器是通过 HTTPS (WSS) 连过来的
    // 解决 "vite外面套了一个https" 导致的热更新失败问题
    hmr: {
      protocol: 'wss',
      clientPort: 443,
    },
    proxy: {
      // API 代理：将 /api/xxx 转发到 http://localhost:8080/xxx
      '/api': {
        target: 'http://localhost:8080',
        secure: false,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      
      // WebSocket 代理：匹配 /websocket 开头的请求
      // 将 wss://.../websocket/xxx 转发到 ws://localhost:8080/websocket/xxx
      '/websocket': {
        target: 'http://localhost:8080',
        ws: true,        // 开启 WebSocket 代理支持
        changeOrigin: true,
        secure: false,
        configure: (proxy, options) => {
          proxy.on('proxyReqWs', (proxyReq, req, socket, options, head) => {
            socket.on('error', (err) => {
              console.error('WebSocket proxy error:', err)
            })
          })
        }
      }
    }
  }
})