import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { jwtDecode } from 'jwt-decode'
import i18n from "@/i18n"

// 类型导入
import type {
  LoginUser,
  LoginUserInfo,
  UserStatus,
  ValidateChatJoinReq,
  ChatRoom,
  Token,
  GuestJoinReq,
  UserLoginState,
} from '@/type'

// API 导入
import { getUserInfoApi } from '@/api/User.ts'
import { loginApi, guestJoinApi, refreshTokenApi } from '@/api/Auth.ts'

// Store 导入
import { useWebsocketStore } from '@/stores/websocketStore.ts'
import { useImageStore } from '@/stores/imageStore'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts'
import { useAppStore } from './appStore'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'

// 错误处理导入
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'

// 校验工具导入
import { decodeJwtPayload, isJwtPayload, isRecord } from '@/utils/validators'

const { t } = i18n.global

type SyncUserStateOptions = {
  loadUserInfo?: boolean
}

type RestoreLoginOptions = SyncUserStateOptions

/**
 * UserStore：管理登录态与当前用户信息
 *
 * 业务职责：
 * - 维护 loginUser（uid/username/accessToken/refreshToken）：用于 HTTP header 与 WS 连接
 * - 维护 loginUserInfo（昵称/头像/简介）：用于 UI 展示
 * - 维护 userStatusList：在线状态表（与 RoomStore 联动）
 */
export const useUserStore = defineStore('user', () => {
  const router = useRouter()
  const appStore = useAppStore()
  // =========================
  // 1. State 定义
  // =========================

  // =========================
  // 1.1 Token 状态管理
  // =========================

  /**
   * Token 状态（响应式）
   * 
   * 业务逻辑：
   * - 存储 accessToken 和 refreshToken
   * - type 字段区分 formal（正式用户）和 guest（访客用户）
   * - 初始状态为 'none'，表示未登录
   * 
   * 注意：
   * - 双 Token 模式：accessToken 用于请求，refreshToken 用于刷新
   * - getAccessToken() 返回当前有效 accessToken
   */
  const token = ref<Token>({
    type: 'none',
    accessToken: undefined,
    refreshToken: undefined
  })

  // =========================
  // 1.2 用户信息状态
  // =========================

  /**
   * 用户详细信息（响应式）
   * 
   * 业务逻辑：
   * - 存储用户的昵称、头像、简介等详细信息
   * - 用于 UI 展示
   * - 通过 syncUserState 自动加载
   */
  const loginUserInfo = ref<LoginUserInfo>()

  /**
   * 用户登录状态（响应式）
   * 
   * 初始状态：'none'，表示未登录
   */
  const loginUser = ref<UserLoginState>({
    type: 'none',
    formal: undefined,
    guest: undefined
  })

  const isLoggingOut = ref(false)
  let refreshTokenPromise: Promise<string | null> | null = null
  let userInfoSyncPromise: Promise<void> | null = null
  let userInfoSyncKey: string | null = null

  /**
   * 获取当前登录用户（formal 或 guest）
   * 
   * 业务逻辑：
   * - 根据 loginUser 的 type 字段返回对应的用户对象
   * - 如果状态为 'none'，返回 null
   * 
   * @returns 当前登录用户，如果没有则返回 null
   */
  const getCurrentLoginUser = (): LoginUser | null => {
    if (loginUser.value.type === 'formal') {
      return loginUser.value.formal
    }
    if (loginUser.value.type === 'guest') {
      return loginUser.value.guest
    }
    return null
  }

  /**
   * 是否为正式用户 (Computed)
   */
  const isFormalUser = computed(() => loginUser.value.type === 'formal')

  // =========================
  // 1.3 其他状态
  // =========================

  /**
   * 用户在线状态列表（响应式）
   * 
   * 业务逻辑：
   * - 存储聊天室内所有用户的在线状态
   * - 与 RoomStore 联动更新
   */
  const userStatusList = ref<UserStatus[]>([])

  /**
   * 验证聊天室加入请求数据（响应式）
   * 
   * 业务逻辑：
   * - 在验证成功后存储请求数据
   * - 供 /join/${chatCode} 页面使用
   */
  const validateChatJoinReq = ref<ValidateChatJoinReq | null>(null)

  /**
   * 验证成功的聊天室信息（响应式）
   * 
   * 业务逻辑：
   * - 在验证成功后存储房间信息
   * - 供 /join/${chatCode} 页面显示房间信息
   */
  const validatedChatRoom = ref<ChatRoom | null>(null)

  /**
   * 重置 Store 内存状态（不清除 localStorage）
   *
   * 业务场景：
   * - App 初始化前：先清空内存态，避免上一次会话残留数据污染本次初始化
   * - 账号切换：先 reset 再按 localStorage 重新拉取
   * - keepAuth: 保留登录态，仅清理会话相关的内存状态
   *
   * 注意：
   * - 该方法不会触发 logout（不会删除 localStorage，也不会主动跳转）
   */
  const resetState = (options?: { keepAuth?: boolean }) => {
    const keepAuth = options?.keepAuth ?? false
    // 只清空内存态，持久化由调用方决定
    if (!keepAuth) {
      loginUser.value = {
        type: 'none',
        formal: undefined,
        guest: undefined
      }
      token.value = {
        type: 'none',
        accessToken: undefined,
        refreshToken: undefined
      }
      loginUserInfo.value = undefined
    }
    userStatusList.value = []
    validateChatJoinReq.value = null
    validatedChatRoom.value = null
  }

  const REFRESH_TOKEN_KEY = 'refreshToken'

  const clearLegacyLoginStorage = () => {
    localStorage.removeItem('loginUser')
    localStorage.removeItem('loginGuest')
  }

  const persistRefreshToken = (refreshToken: string) => {
    if (!refreshToken) {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      clearLegacyLoginStorage()
      return
    }
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    clearLegacyLoginStorage()
  }

  const clearStoredRefreshToken = () => {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    clearLegacyLoginStorage()
  }

  const extractRefreshTokenFromLegacyStorage = (): string => {
    const legacyKeys = ['loginUser', 'loginGuest']
    for (const key of legacyKeys) {
      const raw = localStorage.getItem(key)
      if (!raw) continue
      try {
        const parsed = JSON.parse(raw)
        if (isRecord(parsed) && typeof parsed.refreshToken === 'string' && parsed.refreshToken) {
          persistRefreshToken(parsed.refreshToken)
          return parsed.refreshToken
        }
      } catch {
        // ignore legacy parse errors
      }
    }
    clearLegacyLoginStorage()
    return ''
  }

  const getStoredRefreshToken = (): string => {
    const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (stored) return stored
    if (stored === '') {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    return extractRefreshTokenFromLegacyStorage()
  }

  const buildLoginUserFromRefreshToken = (refreshToken: string): LoginUser | null => {
    const payload = decodeJwtPayload(refreshToken)
    if (!payload) return null
    if (!payload.uid || !payload.username) return null
    return {
      uid: payload.uid,
      username: payload.username,
      accessToken: '',
      refreshToken,
    }
  }


  // =========================
  // 2.1 用户认证相关 Actions
  // =========================

  /**
   * 处理登录请求和数据持久化
   *
   * 业务逻辑：
   * 1. 调用后端登录接口
   * 2. 更新内存状态（loginUser）
   * 3. 持久化 refreshToken 到 localStorage
   * 4. 触发状态同步（token 和 loginUserInfo）
   *
   * 注意：
   * - 登录成功后会自动清除之前的登录状态
   * - 持久化操作有异常处理机制
   *
   * @param username 用户名
   * @param password 密码
   * @returns 登录成功的用户对象（含 token）
   */
  const loginRequest = async (username: string, password: string) => {
    // 1) 调用后端登录接口
    const result = await loginApi({ username, password })

    if (result && result.data) {
      setLoginUser(result.data)
      return result.data
    }
    throw createAppError(
      ErrorType.NETWORK,
      'Login failed',
      {
        severity: ErrorSeverity.ERROR,
        component: 'userStore',
        action: 'loginRequest'
      }
    )
  }


  /**
   * 从 localStorage 恢复正式用户登录状态（仅 refreshToken）
   *
   * 业务目的：
   * - 页面刷新时优先"同步恢复 token"，让后续 API/WS 尽快可用
   * - 用户详情（昵称/头像）可后台再拉取，不阻塞首屏
   *
   * 业务逻辑：
   * 1. 从 localStorage 读取 refreshToken
   * 2. 验证数据格式
   * 3. 更新内存状态
   * 4. 触发状态同步
   *
   * @returns 是否恢复成功
   */
  const restoreLoginUserFromStorage = (options?: RestoreLoginOptions): boolean => {
    const refreshToken = getStoredRefreshToken()
    if (!refreshToken) return false
    try {
      const rebuilt = buildLoginUserFromRefreshToken(refreshToken)
      if (!rebuilt) return false
      if (rebuilt.username === 'guest') return false
      loginUser.value = {
        type: 'formal',
        formal: rebuilt,
        guest: undefined
      }
      // 手动触发状态同步（token 和 loginUserInfo）
      syncUserState(rebuilt, 'formal', options)
      return token.value.type !== 'none' && !!token.value.refreshToken?.token
    } catch {
      return false
    }
  }

  /**
   * 初始化用户信息 (通常在 App 启动或页面刷新时调用)
   * 
   * 业务逻辑：
   * - 优先恢复正式用户登录状态
   * - 如果失败，则恢复访客登录状态
   * - loginUserInfo 的加载由 syncUserState 自动处理，无需手动调用
   */
  const initLoginUserInfo = async () => {
    // 恢复 token（loginUserInfo 会自动通过 syncUserState 加载）
    restoreLoginStateIfNeeded()
  }

  /**
   * 如果内存中没有 token，则尝试从 localStorage 恢复
   *
   * @returns 是否存在有效 token
   */
  const restoreLoginStateIfNeeded = (prefer: 'formal' | 'guest' = 'formal', options?: RestoreLoginOptions): boolean => {
    if (hasToken()) return true
    if (prefer === 'guest') {
      return restoreLoginGuestFromStorage(options) || restoreLoginUserFromStorage(options)
    }
    return restoreLoginUserFromStorage(options) || restoreLoginGuestFromStorage(options)
  }

  /**
   * 主动触发用户信息同步（不阻塞初始化）
   */
  const syncLoginUserInfo = async (): Promise<void> => {
    if (loginUser.value.type === 'formal' && loginUser.value.formal) {
      await syncUserState(loginUser.value.formal, 'formal')
      return
    }
    if (loginUser.value.type === 'guest' && loginUser.value.guest) {
      await syncUserState(loginUser.value.guest, 'guest')
    }
  }

  /**
   * 登出逻辑
   * 
   * 使用场景：
   * - 主动点击退出按钮
   * - Token 过期被动退出
   * 
   * 业务逻辑：
   * 1. 设置加载状态
   * 2. 主动关闭 WebSocket 连接
   * 3. 清除持久化数据
   * 4. 重置所有相关 Store 状态
   * 5. 显示成功消息
   * 6. 跳转首页
   * 
   * 注意：
   * - 使用 replace 跳转避免保留历史记录
   * - 延迟关闭加载状态，让路由守卫先处理
   */
  const logout = async (options?: { showDialog?: boolean; silent?: boolean }) => {
    if (isLoggingOut.value) {
      return
    }
    isLoggingOut.value = true
    const shouldShowDialog = options?.showDialog ?? false
    const silent = options?.silent ?? false

    if (shouldShowDialog) {
      try {
        await showAlertDialog({
          message: t('auth.session_expired') || 'Session expired',
          type: 'warning',
        })
      } catch (e) {
        console.warn('showAlertDialog failed during logout:', e)
      }
    }
    // 设置加载状态
    appStore.isAppLoading = true
    appStore.showLoadingSpinner = true
    appStore.loadingText = t('common.safe_logout') || 'safe logout...'

    try {
      // 1) 主动关闭 WebSocket：避免断线重连继续占用资源
      const websocketStore = useWebsocketStore()
      websocketStore.close()

      // 2) 清除持久化：让刷新后不会"误以为已登录"
      clearStoredRefreshToken()

      // 3) 重置 Store：清空所有与用户相关的数据，防止跨账号串数据
      // 注意：先重置其他Store，最后重置userStore自身
      useMessageStore().resetState()
      useImageStore().resetState()
      useRoomStore().resetState()
      useWebsocketStore().resetState()
      useUserStore().resetState()

      // 4) 显示成功消息
      if (!shouldShowDialog && !silent) {
        ElMessage.success(t('auth.logout_success') || 'log out success')
      }
    } catch (e) {
      // 如果路由跳转失败，至少显示成功消息
      if (!shouldShowDialog && !silent) {
        ElMessage.success(t('auth.logout_success') || 'log out success')
      }

      if (isAppError(e)) {
        throw createAppError(
          ErrorType.ROUTER,
          'Failed to redirect to home page',
          {
            severity: ErrorSeverity.ERROR,
            component: 'userStore',
            action: 'logout',
            originalError: e
          }
        )
      }
      console.error('登出过程中发生错误:', e)
    } finally {
      // 5) 关闭加载状态（延迟执行，让路由守卫先处理）
      setTimeout(async () => {
        try {
          // 跳转首页（使用 replace 避免保留历史记录）
          await router.replace('/')
        } finally {
          appStore.isAppLoading = false
          appStore.showLoadingSpinner = false
          appStore.loadingText = ''
          isLoggingOut.value = false
        }
      }, 500)
    }
  }

  /**
   * 设置验证聊天室加入信息
   * <p>
   * 业务目的：
   * - 在验证成功后存储请求和响应数据
   * - 供 /join/${chatCode} 页面使用，用于显示房间信息和执行实际加入
   *
   * @param req 验证请求对象
   * @param chatRoom 验证成功的房间信息（简化的 ChatRoom）
   */
  const setValidatedJoinChatInfo = (req: ValidateChatJoinReq, chatRoom: ChatRoom) => {
    validateChatJoinReq.value = req
    validatedChatRoom.value = chatRoom
  }

  /**
   * 清除验证聊天室加入信息
   * <p>
   * 业务场景：
   * - 用户离开加入流程时
   * - 开始新的验证时
   * - 成功加入后
   */
  const clearValidatedJoinChatInfo = () => {
    validateChatJoinReq.value = null
    validatedChatRoom.value = null
  }



  // =========================
  // 2.2 访客用户管理 Actions
  // =========================

  /**
   * 从 localStorage 恢复访客登录状态（仅 refreshToken）
   *
   * 业务逻辑：
   * 1. 从 localStorage 读取 refreshToken
   * 2. 验证数据格式
   * 3. 更新内存状态
   * 4. 触发状态同步
   *
   * @returns 是否恢复成功
   */
  const restoreLoginGuestFromStorage = (options?: RestoreLoginOptions): boolean => {
    const refreshToken = getStoredRefreshToken()
    if (!refreshToken) return false
    try {
      const rebuilt = buildLoginUserFromRefreshToken(refreshToken)
      if (!rebuilt) return false
      if (rebuilt.username !== 'guest') return false
      loginUser.value = {
        type: 'guest',
        guest: rebuilt,
        formal: undefined
      }
      // 手动触发状态同步（token 和 loginUserInfo）
      syncUserState(rebuilt, 'guest', options)
      return true;
    } catch (e) {
      isAppError(e)
      throw createAppError(
        ErrorType.STORAGE,
        'Failed to restore login guest from storage',
        {
          severity: ErrorSeverity.ERROR,
          component: 'userStore',
          action: 'restoreLoginGuestFromStorage'
        }
      )
    }
    return false;
  }

  /**
   * 设置访客登录状态
   *
   * 业务逻辑：
   * 1. 更新内存状态
   * 2. 触发状态同步
   * 3. 仅持久化 refreshToken 到 localStorage
   *
   * @param guest 访客用户对象
   */
  const setLoginGuest = (guest: LoginUser) => {
    // 1. 先更新内存状态，确保 UI 响应
    loginUser.value = {
      type: 'guest',
      guest: guest,
      formal: undefined
    }
    // 手动触发状态同步（token 和 loginUserInfo）
    syncUserState(guest, 'guest')

    // 2. 持久化操作进行异常捕获
    try {
      // 只保存 refreshToken
      persistRefreshToken(guest.refreshToken)
    } catch (e) {
      // 使用你定义的错误工厂记录日志
      isAppError(e)
      throw createAppError(
        ErrorType.STORAGE,
        'Failed to save login guest to storage',
        {
          severity: ErrorSeverity.WARNING, // 写入失败通常设为警告，不一定阻塞运行
          component: 'userStore',
          action: 'setLoginGuest'
        }
      )
    }
  }

  /**
   * 清除访客登录状态
   *
   * 业务逻辑：
   * - 将 loginUser 状态重置为 'none'
   * - 仅清除内存状态，不删除 localStorage
   */
  const clearLoginGuest = () => {
    loginUser.value = {
      type: 'none',
      formal: undefined,
      guest: undefined
    }
  }

  /**
   * 设置正式用户登录状态
   *
   * 业务逻辑：
   * 1. 更新内存状态
   * 2. 触发状态同步
   * 3. 仅持久化 refreshToken 到 localStorage
   *
   * @param newloginUser 正式用户对象
   */
  const setLoginUser = (newloginUser: LoginUser) => {
    // 1. 先更新内存状态，确保 UI 响应
    loginUser.value = {
      type: 'formal',
      formal: newloginUser,
      guest: undefined
    }
    // 手动触发状态同步（token 和 loginUserInfo）
    syncUserState(newloginUser, 'formal')

    // 2. 持久化操作进行异常捕获
    try {
      // 只保存 refreshToken
      persistRefreshToken(newloginUser.refreshToken)
    } catch (e) {
      // 使用你定义的错误工厂记录日志
      isAppError(e)
      throw createAppError(
        ErrorType.STORAGE,
        'Failed to save login user to storage',
        {
          severity: ErrorSeverity.WARNING, // 写入失败通常设为警告，不一定阻塞运行
          component: 'userStore',
          action: 'setLoginUser'
        }
      )
    }
  }

  // =========================
  // 2.3 API 封装 Actions
  // =========================

  /**
   * 访客加入聊天室请求
   * 
   * 业务逻辑：
   * 1. 调用后端 API 获取访客登录态
   * 2. 自动更新 loginGuest 并触发状态同步
   * 3. 自动持久化到 localStorage
   * 
   * @param data 访客加入请求数据
   * @returns 登录成功的用户对象（含 token）
   */
  const guestJoinRequest = async (data: GuestJoinReq) => {
    const result = await guestJoinApi(data)

    if (result && result.data) {
      setLoginGuest(result.data)
      return result.data
    }

    throw createAppError(
      ErrorType.NETWORK,
      'Guest join failed',
      {
        severity: ErrorSeverity.ERROR,
        component: 'userStore',
        action: 'guestJoinRequest'
      }
    )
  }

  /**
   * 执行访客加入聊天室流程
   * 
   * 业务逻辑：
   * 1. 调用访客加入 API
   * 2. 初始化应用状态
   * 3. 返回加入结果
   * 
   * @param req 访客加入请求数据
   * @returns 是否加入成功
   */
  const executeGuestJoin = async (req: GuestJoinReq): Promise<boolean> => {
    try {
      const result = await guestJoinRequest(req)

      if (result && result.accessToken) {
        // 初始化应用状态
        await appStore.initializeApp(result.accessToken, 'guest', { waitForRoute: '/chat' })
        return true
      }

      return false
    } catch (e) {
      if (isAppError(e)) {
        throw e
      }
      throw createAppError(
        ErrorType.NETWORK,
        'Guest join execution failed',
        {
          severity: ErrorSeverity.ERROR,
          component: 'userStore',
          action: 'executeGuestJoin',
          originalError: e
        }
      )
    }
  }

  // =========================
  // 2.4 Token 管理 Actions
  // =========================

  /**
   * 获取有效的 Access Token
   * 
   * 业务逻辑：
   * - 检查 accessToken 是否过期
   * - 如果过期，自动使用 refreshToken 刷新
   * - 返回有效的 accessToken
   * 
   * TODO: 后续可在此方法内处理自动刷新逻辑
   * 
   * @returns 有效的 access token
   */
  const getAccessToken = (): string => {
    return token.value.accessToken?.token || ''
  }


  /**
   * 设置 Token
   *
   * 业务逻辑：
   * 1. 验证 accessToken 更新时不能改变用户类型，且不能在 'none' 状态下更新
   * 2. 解码 JWT token 并使用类型守卫验证 payload 结构
   * 3. 根据 whichToken 参数更新对应的 token：
   *    - accessToken: 只能在 formal 或 guest 状态下更新，不能改变用户类型
   *    - refreshToken: 可以更新并改变用户类型（formal/guest）
   *
   * 注意：
   * - accessToken 是可选的（accessToken?），只在 formal 和 guest 类型下存在
   * - refreshToken 是必需的（除了 'none' 类型）
   * - 当 token.type === 'none' 时，不能直接更新 accessToken
   *
   * @param newToken 新的 token 字符串
   * @param whichToken 要设置的 token 类型：'access' 或 'refresh'
   * @param whichType 用户类型：'formal' 或 'guest'
   */
  const setToken = (newToken: string, whichToken: 'access' | 'refresh', whichType: 'formal' | 'guest') => {
    // 验证：更新 accessToken 时不能改变用户类型，且不能在 'none' 状态下更新
    if (whichToken === 'access') {
      if (token.value.type === 'none' || token.value.refreshToken === undefined) {
        throw createAppError(
          ErrorType.VALIDATION,
          'Cannot update accessToken when token type is none or refreshToken is undefined',
          {
            severity: ErrorSeverity.ERROR,
            component: 'userStore',
            action: 'setToken'
          }
        )
      }
      if (whichType !== token.value.type) {
        throw createAppError(
          ErrorType.VALIDATION,
          'Update accessToken type is not allowed',
          {
            severity: ErrorSeverity.ERROR,
            component: 'userStore',
            action: 'setToken'
          }
        )
      }
    }

    // 解码 JWT token（不使用类型断言）
    const decoded = jwtDecode(newToken)

    // 使用类型守卫验证 decoded payload 结构
    if (isJwtPayload(decoded)) {
      if (whichToken === 'access') {
        // 更新 accessToken：只能在 formal 或 guest 状态下更新
        // 此时已经通过上面的检查，确保 token.value.type 是 'formal' 或 'guest'
        if (token.value.type === 'formal' || token.value.type === 'guest') {
          token.value.accessToken = {
            token: newToken,
            payload: decoded
          }
        }
      } else if (whichToken === 'refresh') {
        // 更新 refreshToken：可以改变用户类型
        // 保留现有的 accessToken（如果存在）
        const existingAccessToken =
          token.value.type === 'formal' || token.value.type === 'guest'
            ? token.value.accessToken
            : undefined

        const newRefreshToken = {
          token: newToken,
          payload: decoded
        }
        //根据 whichType 构造符合类型定义的对象，避免使用类型断言
        if (whichType === 'formal') {
          token.value = {
            type: 'formal',
            accessToken: existingAccessToken,
            refreshToken: newRefreshToken
          }
        } else if (whichType === 'guest') {
          token.value = {
            type: 'guest',
            accessToken: existingAccessToken,
            refreshToken: newRefreshToken
          }
        }
      }
    } else {
      // JWT payload 结构验证失败
      throw createAppError(
        ErrorType.VALIDATION,
        'Invalid JWT payload structure',
        {
          severity: ErrorSeverity.ERROR,
          component: 'userStore',
          action: 'setToken'
        }
      )
    }
  }

  /**
   * 检查是否有有效的 Token
   * 
   * 业务逻辑：
   * - 检查 refreshToken.token 是否存在且非空
   * 
   * @returns 是否存在有效的 token
   */
  const hasToken = (): boolean => {
    return !!token.value.refreshToken?.token
  }

  /**
   * 使用 RefreshToken 刷新 AccessToken
   *
   * @returns 新的 AccessToken（失败返回 null）
   */
  const refreshAccessToken = async (): Promise<string | null> => {
    if (refreshTokenPromise) {
      return refreshTokenPromise
    }

    refreshTokenPromise = (async () => {
      const currentType = loginUser.value.type
      if (currentType === 'none') return null

      const refreshToken = token.value.refreshToken?.token
      if (!refreshToken) return null

      try {
        const result = await refreshTokenApi(refreshToken)
        if (result && result.status === 1 && result.data?.accessToken) {
          const newAccessToken = result.data.accessToken
          const newRefreshToken = result.data.refreshToken || refreshToken

          if (currentType === 'formal' && loginUser.value.formal) {
            const updatedUser: LoginUser = {
              ...loginUser.value.formal,
              accessToken: newAccessToken,
              refreshToken: newRefreshToken
            }
            loginUser.value = {
              type: 'formal',
              formal: updatedUser,
              guest: undefined
            }
            persistRefreshToken(newRefreshToken)
          } else if (currentType === 'guest' && loginUser.value.guest) {
            const updatedGuest: LoginUser = {
              ...loginUser.value.guest,
              accessToken: newAccessToken,
              refreshToken: newRefreshToken
            }
            loginUser.value = {
              type: 'guest',
              formal: undefined,
              guest: updatedGuest
            }
            persistRefreshToken(newRefreshToken)
          }

          setToken(newRefreshToken, 'refresh', currentType)
          setToken(newAccessToken, 'access', currentType)
          return newAccessToken
        }
        return null
      } catch (e) {
        console.error('[ERROR] [userStore] Refresh accessToken failed:', e)
        return null
      }
    })()

    try {
      return await refreshTokenPromise
    } finally {
      refreshTokenPromise = null
    }
  }

  // =========================
  // 2.5 用户信息管理 Actions
  // =========================

  /**
   * 获取当前用户ID
   * 
   * 业务逻辑：
   * - 使用 loginUserInfo.uid（因为 loginUserInfo 会自动通过 syncUserState 加载）
   * - 如果 loginUserInfo 未加载，返回空字符串
   * 
   * @returns 当前用户ID
   */
  const getCurrentUserId = (): string => {
    return loginUserInfo.value?.uid || ''
  }

  /**
   * 统一的用户状态同步函数：自动处理 token 同步和 loginUserInfo 加载
   * 
   * 业务逻辑：
   * 1. 同步 token 到 refresh token（用于 HTTP header 和 WS 连接）
   * 2. 自动加载用户详细信息（loginUserInfo），用于 UI 展示
   * 3. 预取用户头像缩略图
   * 
   * 注意：
   * - 异步操作，不阻塞主线程
   * - 失败时静默处理，不影响 token 同步
   * 
   * @param user 用户对象（loginUser 或 loginGuest）
   * @param type 用户类型：formal 或 guest
   */
  const syncUserState = async (user: LoginUser | null, type: 'formal' | 'guest', options?: SyncUserStateOptions) => {
    if (!user || !user.refreshToken) return

    // 1. 同步 refreshToken
    setToken(user.refreshToken, 'refresh', type)
    if (user.accessToken) {
      setToken(user.accessToken, 'access', type)
    }

    const shouldLoadUserInfo = options?.loadUserInfo ?? true
    if (!shouldLoadUserInfo) return

    // 如果没有 token，则不加载用户详细信息
    if (!hasToken()) return
    // 2. 自动加载用户详细信息（异步，不阻塞）
    const uid = user.uid
    if (!uid) return

    const userKey = `${type}:${uid}`
    if (userInfoSyncPromise && userInfoSyncKey === userKey) {
      await userInfoSyncPromise
      return
    }

    userInfoSyncKey = userKey
    userInfoSyncPromise = (async () => {
      try {
        const result = await getUserInfoApi(uid)
        if (result) {
          const imageStore = useImageStore()
          const avatarKey = result.data?.uid ? `user:${result.data.uid}` : ''
          const resolvedAvatar = result.data?.avatar
            ? imageStore.resolveAvatarFromCache(avatarKey, result.data.avatar)
            : undefined
          // 设置用户信息，并添加 userType 字段
          loginUserInfo.value = {
            ...result.data,
            avatar: resolvedAvatar || result.data.avatar,
            userType: type
          }
          useRoomStore().syncLoginMemberInfo(loginUserInfo.value)
          // Store 更新后：预取自己的头像缩略图 blob（不阻塞初始化）
          if (loginUserInfo.value?.avatar) {
            imageStore.ensureThumbBlobUrl(loginUserInfo.value.avatar).then(() => { })
          }
        }
      } catch (e) {
        // 静默失败，不影响 token 同步
        console.error('[ERROR] [userStore] Failed to fetch login user info:', e)
      } finally {
        if (userInfoSyncKey === userKey) {
          userInfoSyncPromise = null
          userInfoSyncKey = null
        }
      }
    })()

    await userInfoSyncPromise
  }


  return {
    // =========================
    // 3.1 用户信息获取
    // =========================
    getCurrentUserId,

    // =========================
    // 3.2 响应式状态
    // =========================
    loginUserInfo,
    userStatusList,
    validateChatJoinReq,
    validatedChatRoom,
    isFormalUser,

    // =========================
    // 3.3 Token 管理
    // =========================
    getAccessToken,
    hasToken,
    restoreLoginStateIfNeeded,
    syncLoginUserInfo,
    refreshAccessToken,
    getRefreshAccessTokenPromise: () => refreshTokenPromise,

    // =========================
    // 3.4 用户状态管理方法
    // =========================
    setLoginGuest,
    restoreLoginGuestFromStorage,
    clearLoginGuest,
    initLoginUserInfo,
    restoreLoginUserFromStorage,

    // =========================
    // 3.5 API 封装方法
    // =========================
    loginRequest,
    guestJoinRequest,
    executeGuestJoin,

    // =========================
    // 3.6 内部状态设置方法（用于特殊情况）
    // =========================
    setLoginUser,
    logout,
    resetState,
    setValidatedJoinChatInfo,
    clearValidatedJoinChatInfo,
  }
})
