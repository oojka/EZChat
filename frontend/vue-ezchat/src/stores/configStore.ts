import {computed} from 'vue'
import {defineStore} from 'pinia'

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
     * 需要限流拦截的 API 路径列表
     * 
     * 业务逻辑：
     * - 只有在此列表中的 API 才会进行限流检查
     * - 支持路径匹配（使用 includes 判断）
     * 
     * 示例：
     * - '/auth/login' - 精确匹配登录接口
     * - '/chat' - 匹配所有聊天相关接口
     * - '/user' - 匹配所有用户相关接口
     */
    const RATE_LIMITED_APIS = [
      '/auth/login',
      '/auth/join',
      '/auth/validate',
      '/auth/guest',
      '/auth/register',
      '/auth/register/upload',
      '/message/upload',
      '/chat/join',
      // 可以继续添加需要限流的 API 路径
    ]

    /**
     * 不需要 Token 的 API 路径列表
     * 
     * 业务逻辑：
     * - 只有在此列表中的 API 不需要添加 token
     * - 支持路径匹配（使用 includes 判断）
     * - 通常包括：登录、注册、访客加入、验证等公开接口
     * 
     * 注意：
     * - 如果 API 不在列表中，默认需要 token
     * - 列表中的 API 即使有 token 也不会添加
     */
    const NO_TOKEN_APIS = [
      '/auth/login',
      '/auth/register',
      '/auth/register/upload',
      '/auth/guest',
      '/auth/join',
      '/auth/validate',
    ]

    /**
     * 限流配置参数
     * 
     * 业务逻辑：
     * - MAX_REQUESTS_PER_WINDOW: 每个时间窗口内允许的最大请求数
     * - WINDOW_SIZE: 滑动窗口的时间大小（毫秒）
     * - LOCKOUT_DURATION: 超过限制后的封锁持续时间（毫秒）
     */
    const MAX_REQUESTS_PER_WINDOW = 20
    const WINDOW_SIZE = 3 * 1000 // 3 秒
    const LOCKOUT_DURATION = 15 * 1000 // 15 秒

    /**
     * 判断是否本地开发环境（localhost / 127.0.0.1）
     *
     * 业务原因：本地开发通常需要直连 :8080；生产环境更推荐走同源反代（避免 https → http 混合内容）。
     */
    const isDevLocal = () => {
      const host = window.location.hostname
      return host === 'localhost' || host === '127.0.0.1'
    }

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
     * 2. WebSocket 基础地址 (不含 Token)
     * 自动适配协议 (ws/wss)
     * 
     * 注意：
     * - API 请求地址不需要在这里计算，因为：
     *   1. 开发环境：vite.config.ts 中的代理已经处理了 /api 到 localhost:8080 的转发
     *   2. 生产环境：通常使用同源请求，直接使用 /api 即可
     * - request.ts 中直接使用 baseURL: "/api" 即可
     */
    const websocketUrl = computed(() => {
      try {
        const urlObj = new URL(baseUrl.value)

        // 协议自适应：HTTPS -> wss, HTTP -> ws
        urlObj.protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'

        // 仅在本地开发时替换端口，生产环境走同源反代（避免 wss 直连 :8080 导致握手失败）
        if (isDevLocal()) urlObj.port = API_PORT.toString()
        urlObj.pathname = '/websocket' // 注意：不带末尾斜杠，也不带 Token

        return urlObj.toString()
      } catch (e) {
        console.error('[WARN] [CONFIG] WS URL Construction Error:', e)
        return ''
      }
    })

    return {
      baseUrl,
      websocketUrl,
      // 导出配置常量
      RATE_LIMITED_APIS,
      NO_TOKEN_APIS,
      MAX_REQUESTS_PER_WINDOW,
      WINDOW_SIZE,
      LOCKOUT_DURATION,
    }
  },
)
