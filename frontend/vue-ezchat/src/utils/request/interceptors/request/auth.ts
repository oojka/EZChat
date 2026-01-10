import type { InternalAxiosRequestConfig } from 'axios'
import { createAppError, ErrorSeverity, ErrorType } from '@/error/ErrorTypes'
import { useConfigStore } from '@/stores/configStore'
import { useUserStore } from '@/stores/userStore'
import { useWebsocketStore } from '@/stores/websocketStore'
import { getRefreshPromise } from '../../refresh'
import { isRefreshRequest, needsToken } from '../../utils'

export const createAuthRequestInterceptor = () => {
  return async (config: InternalAxiosRequestConfig) => {
    const requestUrl = config.url || ''
    const configStore = useConfigStore()
    const userStore = useUserStore()
    const websocketStore = useWebsocketStore()

    const needsTokenHeader = needsToken(requestUrl, configStore.NO_TOKEN_APIS)
    if (needsTokenHeader) {
      // 必须检查 localStorage 中是否存在 refreshToken，防止用户删除存储后仍能使用 token
      const storedRefreshToken = localStorage.getItem('refreshToken')
      if (storedRefreshToken === '') {
        localStorage.removeItem('refreshToken')
      }
      const hasLoginInStorage = !!storedRefreshToken

      if (!hasLoginInStorage) {
        websocketStore.close()
        void userStore.logout({ showDialog: true })
        // 使用 createAppError 创建标记错误，避免响应拦截器重复显示错误消息
        return Promise.reject(createAppError(
          ErrorType.ROUTER,
          'Session expired',
          {
            severity: ErrorSeverity.WARNING,
            component: 'requestInterceptor',
            action: 'checkToken'
          }
        ))
      }

      // localStorage 中存在登录信息，但内存中没有 token 时，尝试恢复
      userStore.restoreLoginStateIfNeeded()

      const refreshPromise = getRefreshPromise()
      if (refreshPromise && !isRefreshRequest(config)) {
        const refreshedToken = await refreshPromise
        if (!refreshedToken) {
          return Promise.reject(createAppError(
            ErrorType.ROUTER,
            'Session expired',
            {
              severity: ErrorSeverity.WARNING,
              component: 'requestInterceptor',
              action: 'waitRefresh'
            }
          ))
        }
      }

      // 每次请求都获取最新的 token（响应式，自动获取最新值）
      const currentToken = userStore.getAccessToken()
      if (currentToken) {
        config.headers.token = currentToken
      }
    }

    return config
  }
}
