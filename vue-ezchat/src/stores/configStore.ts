import {computed} from 'vue'
import {defineStore} from 'pinia'

// @ts-ignore
// @ts-ignore
/**
 * ConfigStore: 管理全局网络配置与 URL 构造
 * 采用原生 URL 类处理逻辑，确保在 IP、域名或穿透环境下均能稳定工作
 */
export const useConfigStore = defineStore(
  'config',
  () => {
    // 后端服务端口常量
    const API_PORT = 8080

    /**
     * 1. 基础源地址 (Base Origin)
     * 优先使用环境变量，否则动态捕获当前浏览器地址
     */
    const baseUrl = computed(() => {
      // 如果环境变量配置了完整的 WS URL，则直接使用（通常用于生产环境）
      // 但这里逻辑是获取 Base Origin，所以建议 VITE_API_URL
      return window.location.origin
    })

    /**
     * 2. API 请求地址
     * 将当前地址的端口替换为后端端口，并附加 /api 路径
     */
    const apiUrl = computed(() => {
      try {
        const urlObj = new URL(baseUrl.value)
        // 如果是在开发环境 (localhost)，通常前端 5173，后端 8080
        // 如果是生产环境，通常是同源的，或者通过 Nginx 代理
        // 这里保留原逻辑：强制替换端口
        urlObj.port = API_PORT.toString()
        urlObj.pathname = '/api'
        return urlObj.toString()
      } catch (e) {
        console.error('[ERROR] [CONFIG] Invalid Base URL:', e)
        return ''
      }
    })

    /**
     * 3. WebSocket 基础地址 (不含 Token)
     * 自动适配协议 (ws/wss)
     */
    const websocketUrl = computed(() => {
      try {
        const urlObj = new URL(baseUrl.value)

        // 协议自适应：HTTPS -> wss, HTTP -> ws
        urlObj.protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'

        // 设置端口与路径
        urlObj.port = API_PORT.toString()
        urlObj.pathname = '/websocket' // 注意：不带末尾斜杠，也不带 Token

        return urlObj.toString()
      } catch (e) {
        console.error('[WARN] [CONFIG] WS URL Construction Error:', e)
        return ''
      }
    })

    return {
      baseUrl,
      apiUrl,
      websocketUrl,
    }
  },
)
