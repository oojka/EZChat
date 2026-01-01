import {computed, ref} from 'vue'
import {defineStore} from 'pinia'
import type {LoginUser, LoginUserInfo, UserStatus} from '@/type'
import {getUserInfoApi} from '@/api/User.ts'
import {loginApi} from '@/api/Auth.ts'
import {ElMessage} from 'element-plus'
import {useWebsocketStore} from '@/stores/websocketStore.ts' // 引入 websocketStore

/**
 * UserStore：管理登录态与当前用户信息
 *
 * 业务职责：
 * - 维护 loginUser（uid/username/token）：用于 HTTP header 与 WS 连接
 * - 维护 loginUserInfo（昵称/头像/简介）：用于 UI 展示
 * - 维护 userStatusList：在线状态表（与 RoomStore 联动）
 */
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
   *
   * @param username 用户名
   * @param password 密码
   * @returns 登录成功的用户对象（含 token）
   */
  const loginRequest = async (username: string, password: string) => {
    // 1) 调用后端登录接口
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
    // 1) 主动关闭 WebSocket：避免断线重连继续占用资源
    const websocketStore = useWebsocketStore()
    websocketStore.close()

    // 2) 清除持久化：让刷新后不会“误以为已登录”
    localStorage.removeItem('loginUser')

    // 3) 重置 Store：清空所有与用户相关的数据，防止跨账号串数据
    loginUser.value = {
      uid: '',
      username: '',
      token: '',
    }
    loginUserInfo.value = undefined
    userStatusList.value = []
  }

  /**
   * 获取当前 token（用于少数需要 computed 的场景）
   */
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
