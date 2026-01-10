import { useUserStore } from '@/stores/userStore'

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
