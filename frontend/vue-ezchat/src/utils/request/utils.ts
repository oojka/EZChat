import type { InternalAxiosRequestConfig } from 'axios'
import type { RetryableRequestConfig } from '@/type'

export const isRefreshRequest = (config?: InternalAxiosRequestConfig): boolean => {
  const url = config?.url || ''
  return url.includes('/auth/refresh')
}

export const isSafeRetryMethod = (config?: InternalAxiosRequestConfig): boolean => {
  const method = (config?.method || 'get').toUpperCase()
  return method === 'GET' || method === 'HEAD'
}

/**
 * 判断当前请求是否需要限流
 * 
 * @param url 请求 URL
 * @param rateLimitedApis 需要限流的 API 路径列表
 * @returns 是否需要限流
 */
export const shouldRateLimit = (url: string, rateLimitedApis: string[]): boolean => {
  return rateLimitedApis.some(apiPath => url.includes(apiPath))
}

/**
 * 判断当前请求是否需要 Token
 * 
 * @param url 请求 URL
 * @param noTokenApis 不需要 Token 的 API 路径列表
 * @returns 是否需要 Token
 */
export const needsToken = (url: string, noTokenApis: string[]): boolean => {
  return !noTokenApis.some(apiPath => url.includes(apiPath))
}

export const getRequestToken = (config?: InternalAxiosRequestConfig): string | undefined => {
  const headers = config?.headers
  if (!headers) return undefined
  const token = typeof headers.get === 'function' ? headers.get('token') : headers['token']
  return typeof token === 'string' ? token : undefined
}

export const canRetryWithCurrentToken = (config: RetryableRequestConfig, currentToken: string): boolean => {
  const reqToken = getRequestToken(config)
  return !!reqToken && reqToken !== currentToken && config._retryToken !== currentToken
}
