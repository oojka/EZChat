import type { AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { ErrorCode } from '@/error/ErrorCode'
import { useUserStore } from '@/stores/userStore'
import { extractErrorCode } from '@/utils/validators'
import type { ErrorCodeValue, RetryableRequestConfig } from '@/type'
import { canRetryWithCurrentToken, isRefreshRequest, isSafeRetryMethod } from './utils'

const { t } = i18n.global

const TOKEN_EXPIRED_CODES = new Set<ErrorCodeValue>([ErrorCode.TOKEN_EXPIRED])

const isTokenExpiredCode = (code: ErrorCodeValue | null): boolean => {
  if (code === null) return false
  return TOKEN_EXPIRED_CODES.has(code)
}

const refreshAccessToken = async (): Promise<string | null> => {
  const userStore = useUserStore()
  if (!userStore.hasToken()) return null
  return userStore.refreshAccessToken()
}

export const getRefreshPromise = (): Promise<string | null> | null => {
  const userStore = useUserStore()
  return userStore.getRefreshAccessTokenPromise()
}

export const getTokenForRetry = async (): Promise<string | null> => {
  const userStore = useUserStore()
  const inFlight = userStore.getRefreshAccessTokenPromise()
  if (inFlight) return inFlight
  return refreshAccessToken()
}

export const retryWithFreshToken = async (
  request: AxiosInstance,
  config: RetryableRequestConfig
): Promise<unknown> => {
  const token = await getTokenForRetry()
  if (!token) {
    throw new Error('REFRESH_FAILED')
  }
  config.headers = config.headers || {}
  config.headers.token = token
  return request(config)
}

export const handleUnauthorized = async (
  request: AxiosInstance,
  config: RetryableRequestConfig | undefined,
  responseData: unknown
): Promise<unknown> => {
  const userStore = useUserStore()

  if (!config || isRefreshRequest(config)) {
    void userStore.logout({ showDialog: true })
    return false
  }

  const errorCode = extractErrorCode(responseData)
  if (!isTokenExpiredCode(errorCode)) {
    void userStore.logout({ showDialog: true })
    return false
  }

  const safeMethod = isSafeRetryMethod(config)
  if (!safeMethod) {
    const token = await getTokenForRetry()
    if (!token) {
      void userStore.logout({ showDialog: true })
      return false
    }
    ElMessage.warning(t('api.retry_required'))
    return false
  }

  if (config._retry) {
    void userStore.logout({ showDialog: true })
    return false
  }

  const currentToken = userStore.getAccessToken()
  if (currentToken && canRetryWithCurrentToken(config, currentToken)) {
    config._retry = true
    config._retryToken = currentToken
    config.headers = config.headers || {}
    config.headers.token = currentToken
    return request(config)
  }

  config._retry = true
  try {
    const retried = await retryWithFreshToken(request, config)
    return retried
  } catch {
    void userStore.logout({ showDialog: true })
    return false
  }
}
