/**
 * 应用级 Token 刷新 Composable
 *
 * 核心职责：
 * - 检查并恢复登录状态（从 localStorage）
 * - 按需刷新 Access Token
 * - 提供应用启动时的认证恢复逻辑
 *
 * 使用示例：
 * ```vue
 * const { refreshOnceIfNeeded } = useAppRefreshToken()
 * const { hadToken, token } = await refreshOnceIfNeeded()
 * ```
 *
 * @module useAppRefreshToken
 */
import { useUserStore } from '@/stores/userStore'

/**
 * 应用级 Token 刷新 Hook
 *
 * @returns refreshOnceIfNeeded - 检查并刷新 Token 的方法
 */
export const useAppRefreshToken = () => {
  const userStore = useUserStore()

  const refreshOnceIfNeeded = async (): Promise<{ hadToken: boolean; token: string | null }> => {
    const hasToken = userStore.restoreLoginStateIfNeeded('formal', { loadUserInfo: false })
    if (!hasToken) return { hadToken: false, token: null }

    const newToken = await userStore.refreshAccessToken()
    return { hadToken: true, token: newToken }
  }

  return {
    refreshOnceIfNeeded,
  }
}
