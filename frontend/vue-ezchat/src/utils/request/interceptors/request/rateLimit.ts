import type { InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'
import { useConfigStore } from '@/stores/configStore'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'
import { shouldRateLimit } from '../../utils'

const { t } = i18n.global

export const createRateLimitRequestInterceptor = () => {
  return (config: InternalAxiosRequestConfig) => {
    const requestUrl = config.url || ''
    const configStore = useConfigStore()
    const isRateLimited = shouldRateLimit(requestUrl, configStore.RATE_LIMITED_APIS)

    // 只对需要限流的 API 进行限流检查
    if (isRateLimited) {
      const now = Date.now()
      const restrictionUntil = localStorage.getItem('api_restriction_until')

      if (restrictionUntil) {
        const until = parseInt(restrictionUntil)
        if (!isNaN(until) && until > now) {
          ElMessage.closeAll()
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('loginUser')
          localStorage.removeItem('loginGuest')

          // 处于封锁期：直接跳错误页（使用 state 传递错误码，避免 URL 暴露敏感参数）
          router.replace({
            path: '/error',
            state: { code: '429' }
          }).catch(() => { })

          return Promise.reject(new Error('REDIRECT_TO_ERROR_PAGE'))
        } else {
          localStorage.removeItem('api_restriction_until')
        }
      }

      let windowStart = parseInt(localStorage.getItem('api_window_start') || '0')
      let requestCount = parseInt(localStorage.getItem('api_req_count') || '0')

      // 1) 滑动窗口计数：超过窗口期则重置计数
      if (now - windowStart > configStore.WINDOW_SIZE) {
        windowStart = now
        requestCount = 1
        localStorage.setItem('api_window_start', windowStart.toString())
        localStorage.setItem('api_req_count', requestCount.toString())
      } else {
        // 2) 窗口期内：超过上限则进入封锁期
        if (requestCount >= configStore.MAX_REQUESTS_PER_WINDOW) {
          const lockoutTime = now + configStore.LOCKOUT_DURATION
          localStorage.setItem('api_restriction_until', lockoutTime.toString())
          ElMessage.error(t('api.too_many_requests'))
          showAlertDialog({
            title: 'api.too_many_requests',
            message: 'api.too_many_requests',
            confirmText: 'common.confirm',
            type: 'error',
            onConfirm: () => {
              return Promise.reject(new Error('429_SIMULATED'))
            }
          })
          return Promise.reject(new Error('429_SIMULATED'))
        } else {
          requestCount++
          localStorage.setItem('api_req_count', requestCount.toString())
        }
      }
    }

    return config
  }
}
