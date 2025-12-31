import {computed, ref} from 'vue'
import {defineStore} from 'pinia'
import type {LoginUser, LoginUserInfo, UserStatus} from '@/type'
import {getUserInfoApi} from '@/api/User.ts'
import {loginApi} from '@/api/Auth.ts'
import {ElMessage} from 'element-plus'
import {useWebsocketStore} from '@/stores/websocketStore.ts' // 引入 websocketStore

export const useUserStore = defineStore('user', () => {
  // =========================
  // 1. State 定义
  // =========================
  const loginUserInfo = ref<LoginUserInfo>()

  // 初始化为空对象，避免 null 检查过于繁琐
  const loginUser = ref<LoginUser>({
    uid: '',
    username: '',
    token: '',
  })

  const userStatusList = ref<UserStatus[]>([])

  // =========================
  // 2. Actions
  // =========================

  /**
   * 处理登录请求和数据持久化
   */
  const loginRequest = async (username: string, password: string) => {
    const result = await loginApi(username, password)
    if (result && result.data) {
      // 1. 更新 State
      loginUser.value = result.data
      // 2. 持久化
      localStorage.setItem('loginUser', JSON.stringify(result.data))
      return result.data
    }
    throw new Error('Login failed')
  }

  /**
   * 初始化用户信息 (通常在 App 启动或页面刷新时调用)
   */
  const initLoginUserInfo = async () => {
    const rowString: string | null = localStorage.getItem('loginUser')
    if (rowString) {
      try {
        // 1. 恢复 Token 等基础信息
        loginUser.value = JSON.parse(rowString)

        // 2. 根据 UID 拉取详细用户信息
        const result = await getUserInfoApi(loginUser.value.uid)
        if (result) {
          loginUserInfo.value = result.data
        }
      } catch (e) {
        ElMessage.error('ログイン情報取得に失敗しました。')
        logout()
      }
    }
  }

  /**
   * ★★★ 新增：登出逻辑 ★★★
   * 用于：主动点击退出按钮 或 Token 过期被动退出
   */
  const logout = () => {
    // 1. 关闭 WebSocket 连接 (新增)
    const websocketStore = useWebsocketStore()
    websocketStore.close()

    // 2. 清除持久化存储
    localStorage.removeItem('loginUser')

    // 3. 重置 Store 状态
    loginUser.value = {
      uid: '',
      username: '',
      token: '',
    }
    loginUserInfo.value = undefined
    userStatusList.value = []
  }

  const getToken = computed<string>(() => {
    return loginUser.value.token
  })

  return {
    loginUser,
    loginUserInfo,
    userStatusList,
    getToken,
    initLoginUserInfo,
    loginRequest,
    logout,
  }
})
