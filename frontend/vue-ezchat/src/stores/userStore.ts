import {computed, ref} from 'vue'
import {defineStore} from 'pinia'
import type {LoginUser, LoginUserInfo, UserStatus} from '@/type'
import {getUserInfoApi} from '@/api/User.ts'
import {loginApi} from '@/api/Auth.ts'
import {ElMessage} from 'element-plus'
import {useWebsocketStore} from '@/stores/websocketStore.ts' // 引入 websocketStore
import { useImageStore } from '@/stores/imageStore'

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
  /**
   * 重置 Store 内存状态（不清除 localStorage）
   *
   * 业务场景：
   * - App 初始化前：先清空内存态，避免上一次会话残留数据污染本次初始化
   * - 账号切换：先 reset 再按 localStorage 重新拉取
   *
   * 注意：
   * - 该方法不会触发 logout（不会删除 localStorage，也不会主动跳转）
   */
  const resetState = () => {
    // 只清空内存态，持久化由调用方决定
    loginUser.value = { uid: '', username: '', token: '' }
    loginUserInfo.value = undefined
    userStatusList.value = []
  }


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
   * 从 localStorage 恢复登录用户（仅恢复 token/uid/username，不发请求）
   *
   * 业务目的：
   * - refresh 时优先“同步恢复 token”，让后续 API/WS 尽快可用
   * - 用户详情（昵称/头像）可后台再拉取，不阻塞首屏
   *
   * @returns 是否恢复成功
   */
  const restoreLoginUserFromStorage = (): boolean => {
    const rowString: string | null = localStorage.getItem('loginUser')
    if (!rowString) return false
    try {
      loginUser.value = JSON.parse(rowString)
      return !!loginUser.value?.token
    } catch {
      return false
    }
  }

  /**
   * 拉取并更新登录用户详情（昵称/头像等）
   *
   * 业务目的：
   * - 将“用户详情请求”从 refresh 主链路中解耦，避免黑屏转圈时间被拉长
   */
  const fetchLoginUserInfo = async () => {
    if (!loginUser.value?.uid) return
    const result = await getUserInfoApi(loginUser.value.uid)
    if (result) {
      const imageStore = useImageStore()
      const prevAvatar = loginUserInfo.value?.avatar
      loginUserInfo.value = result.data
      // Store 更新后：预取自己的头像缩略图 blob（不阻塞初始化）
      if (prevAvatar && loginUserInfo.value?.avatar) {
        imageStore.revokeUnusedBlobs([prevAvatar], [loginUserInfo.value.avatar])
      }
      if (loginUserInfo.value?.avatar) imageStore.ensureThumbBlobUrl(loginUserInfo.value.avatar).then(() => {})
    }
  }

  /**
   * 初始化用户信息 (通常在 App 启动或页面刷新时调用)
   */
  const initLoginUserInfo = async () => {
    try {
      if (!restoreLoginUserFromStorage()) return
      await fetchLoginUserInfo()
    } catch (e) {
      ElMessage.error('ログイン情報取得に失敗しました。')
      logout()
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
    restoreLoginUserFromStorage,
    fetchLoginUserInfo,
    loginRequest,
    logout,
    resetState,
  }
})
