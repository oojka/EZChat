import type { AxiosError, AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'
import { ErrorType, isAppError } from '@/error/ErrorTypes'
import type { RetryableRequestConfig } from '@/type'
import { handleUnauthorized } from '../../refresh'

const { t } = i18n.global

const isAxiosError = (error: unknown): error is AxiosError => {
  return typeof error === 'object' && error !== null && 'isAxiosError' in error
}

export const createHttpErrorHandler = (request: AxiosInstance) => {
  return async (error: unknown) => {
    // 如果是请求拦截器已经处理过的会话过期错误，直接跳过，避免重复显示
    if (isAppError(error) && error.type === ErrorType.ROUTER && error.message === 'Session expired') {
      return Promise.reject(error)
    }

    if (!isAxiosError(error) || !error.response) {
      ElMessage.error(t('api.network_error'))
      return Promise.reject(error)
    }

    const { response } = error
    const status = response.status

    switch (status) {
      case 400:
        // HTTP 400：请求参数错误，不跳转（用户可修正）
        ElMessage.error(t('api.http_400'))
        break

      case 401:
        {
          const config: RetryableRequestConfig | undefined = error.config
          return handleUnauthorized(request, config, response?.data)
        }

      case 403:
        // HTTP 403：权限被拒绝
        ElMessage.error(t('api.no_permission') || 'No permission')
        router.replace('/').catch(() => { })
        break

      case 404:
        // HTTP 404：资源未找到
        router.replace({ path: '/404', state: { code: '404' } }).catch(() => { })
        break

      case 408:
        // HTTP 408：请求超时，不跳转（用户可重试）
        ElMessage.error(t('api.http_408') || 'Request timeout')
        break

      case 429:
        // HTTP 429：服务端限流，前端也进入短暂封锁期，减少重复请求
        ElMessage.error(t('api.too_many_requests'))
        localStorage.setItem('api_restriction_until', (Date.now() + 30000).toString())
        router.replace({ path: '/error', state: { code: '429' } }).catch(() => { })
        break

      case 502:
      case 503:
      case 504:
        // HTTP 502/503/504：网关错误/服务不可用/网关超时
        router.replace({ path: '/error', state: { code: status.toString() } }).catch(() => { })
        break

      default:
        if (status >= 500) {
          // HTTP 500+：服务器错误
          router.replace({ path: '/error', state: { code: status.toString() } }).catch(() => { })
        } else {
          // 其他未处理的 HTTP 错误
          ElMessage.error(t('api.unexpected_error'))
        }
    }

    return Promise.reject(error)
  }
}
