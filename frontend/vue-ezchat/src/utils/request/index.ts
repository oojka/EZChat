import axios from 'axios'
import { createAuthRequestInterceptor } from './interceptors/request/auth'
import { createRateLimitRequestInterceptor } from './interceptors/request/rateLimit'
import { createBusinessResponseHandler } from './interceptors/response/business'
import { createHttpErrorHandler } from './interceptors/response/http'

/**
 * Axios 实例（全局请求入口）
 *
 * 约定：
 * - baseURL 使用 `/api`：由 Vite dev proxy 转发到后端真实路径（后端本身不带 `/api` 前缀）
 * - Token 通过请求头 `token` 传递（不是 Authorization Bearer）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 600000,
})

request.interceptors.request.use(createRateLimitRequestInterceptor(), (error) => Promise.reject(error))
request.interceptors.request.use(createAuthRequestInterceptor(), (error) => Promise.reject(error))

request.interceptors.response.use(
  createBusinessResponseHandler(request),
  createHttpErrorHandler(request),
)

export default request
