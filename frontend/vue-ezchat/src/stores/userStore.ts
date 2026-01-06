import { ref } from 'vue'
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
  JoinChatReq,
  JwtPayload,
  UserLoginState,
  LoginForm
} from '@/type'

// API 导入
import { getUserInfoApi } from '@/api/User.ts'
import { loginApi, guestJoinApi } from '@/api/Auth.ts'

// Store 导入
import { useWebsocketStore } from '@/stores/websocketStore.ts'
import { useImageStore } from '@/stores/imageStore'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts'
import { useAppStore } from './appStore'

// 错误处理导入
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'

// 校验工具导入
import { isLoginUser, isJwtPayload } from '@/utils/validators'

const { t } = i18n.global
/**
 * UserStore：管理登录态与当前用户信息
 *
 * 业务职责：
 * - 维护 loginUser（uid/username/token）：用于 HTTP header 与 WS 连接
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
   * - 当前双 token 模式暂未实现，accessToken 为占位符
   * - 实际使用中，getAccessToken() 返回 refreshToken
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
   *
   * 注意：
   * - 该方法不会触发 logout（不会删除 localStorage，也不会主动跳转）
   */
  const resetState = () => {
    // 只清空内存态，持久化由调用方决定
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
    userStatusList.value = []
    validateChatJoinReq.value = null
    validatedChatRoom.value = null
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
   * 3. 持久化到 localStorage
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
      // 1. 更新内存 State
      loginUser.value = {
        type: 'formal',
        formal: result.data,
        guest: undefined
      }

      // 2. 持久化（增加健壮性）
      try {
        // 先删除之前的登录用户
        localStorage.removeItem('loginGuest')
        localStorage.removeItem('loginUser')
        // 再保存新的登录用户
        localStorage.setItem('loginUser', JSON.stringify(result.data))
      } catch (e) {
        isAppError(e)
        throw createAppError(
          ErrorType.STORAGE,
          'Failed to save login user to storage',
          {
            severity: ErrorSeverity.WARNING,
            component: 'userStore',
            action: 'loginRequest'
          }
        )
      }
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
   * 从 localStorage 恢复正式用户登录状态
   *
   * 业务目的：
   * - 页面刷新时优先"同步恢复 token"，让后续 API/WS 尽快可用
   * - 用户详情（昵称/头像）可后台再拉取，不阻塞首屏
   *
   * 业务逻辑：
   * 1. 从 localStorage 读取 loginUser
   * 2. 验证数据格式
   * 3. 更新内存状态
   * 4. 触发状态同步
   *
   * @returns 是否恢复成功
   */
  const restoreLoginUserFromStorage = (): boolean => {
    const rowString: string | null = localStorage.getItem('loginUser')
    if (!rowString) return false
    try {
      const parsed = JSON.parse(rowString)
      if (isLoginUser(parsed)) {
        loginUser.value = {
          type: 'formal',
          formal: parsed,
          guest: undefined
        }
        // 手动触发状态同步（token 和 loginUserInfo）
        syncUserState(parsed, 'formal')
        return token.value.type !== 'none' && !!token.value.refreshToken?.token
      }
      return false
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
    restoreLoginUserFromStorage() || restoreLoginGuestFromStorage()
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
  const logout = async () => {
    // 设置加载状态
    appStore.isAppLoading = true
    appStore.showLoadingSpinner = true
    appStore.loadingText = t('common.safe_logout') || 'safe logout...'

    try {
      // 1) 主动关闭 WebSocket：避免断线重连继续占用资源
      const websocketStore = useWebsocketStore()
      websocketStore.close()

      // 2) 清除持久化：让刷新后不会"误以为已登录"
      localStorage.removeItem('loginUser')
      localStorage.removeItem('loginGuest')

      // 3) 重置 Store：清空所有与用户相关的数据，防止跨账号串数据
      // 注意：先重置其他Store，最后重置userStore自身
      useMessageStore().resetState()
      useImageStore().resetState()
      useRoomStore().resetState()
      useWebsocketStore().resetState()
      useUserStore().resetState()

      // 4) 显示成功消息
      ElMessage.success(t('auth.logout_success') || 'log out success')
    } catch (e) {
      // 如果路由跳转失败，至少显示成功消息
      ElMessage.success(t('auth.logout_success') || 'log out success')

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
        // 跳转首页（使用 replace 避免保留历史记录）
        await router.replace('/')
        appStore.isAppLoading = false
        appStore.showLoadingSpinner = false
        appStore.loadingText = ''
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
   * 从 localStorage 恢复访客登录状态
   *
   * 业务逻辑：
   * 1. 从 localStorage 读取 loginGuest
   * 2. 验证数据格式
   * 3. 更新内存状态
   * 4. 触发状态同步
   *
   * @returns 是否恢复成功
   */
  const restoreLoginGuestFromStorage = (): boolean => {
    const rowString: string | null = localStorage.getItem('loginGuest')
    if (!rowString) return false
    try {
      const parsed = JSON.parse(rowString)
      if (isLoginUser(parsed)) {
        loginUser.value = {
          type: 'guest',
          guest: parsed,
          formal: undefined
        }
        // 手动触发状态同步（token 和 loginUserInfo）
        syncUserState(parsed, 'guest')
        return true;
      }
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
   * 3. 持久化到 localStorage
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
      // 先删除之前的登录用户
      localStorage.removeItem('loginUser')
      localStorage.removeItem('loginGuest')
      // 再保存新的登录用户
      localStorage.setItem('loginGuest', JSON.stringify(guest))
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
   * 3. 持久化到 localStorage
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
      // 先删除之前的登录用户
      localStorage.removeItem('loginGuest')
      localStorage.removeItem('loginUser')
      // 再保存新的登录用户
      localStorage.setItem('loginUser', JSON.stringify(newloginUser))
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
   * @param currentChatCode 当前聊天室代码（用于路由跳转）
   * @returns 是否加入成功
   */
  const executeGuestJoin = async (req: GuestJoinReq, currentChatCode: string): Promise<boolean> => {
    try {
      const result = await guestJoinRequest(req)
      
      if (result && result.token) {
        // 初始化应用状态
        await appStore.initializeApp(result.token, 'guest')
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
   * TODO: 双 token 模式实现后，在此方法内处理自动刷新逻辑
   * 当前实现：返回 refreshToken（因为 accessToken 还未实现）
   * 
   * @returns 有效的 access token
   */
  const getAccessToken = (): string => {
    // 当前临时返回 refreshToken
    // TODO: 未来实现：检查过期 -> 自动刷新 -> 返回 accessToken
    return token.value.refreshToken?.token || ''
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
  const syncUserState = async (user: LoginUser | null, type: 'formal' | 'guest') => {
    if (!user || !user.token) return

    // 1. 同步 token 到 refresh token
    setToken(user.token, 'refresh', type)

    // 如果没有 token，则不加载用户详细信息
    if (!hasToken()) return
    // 2. 自动加载用户详细信息（异步，不阻塞）
    const uid = user.uid
    if (!uid) return

    try {
      const result = await getUserInfoApi(uid)
      if (result) {
        const imageStore = useImageStore()
        const prevAvatar = loginUserInfo.value?.avatar
        loginUserInfo.value = result.data
        // Store 更新后：预取自己的头像缩略图 blob（不阻塞初始化）
        if (prevAvatar && loginUserInfo.value?.avatar) {
          imageStore.revokeUnusedBlobs([prevAvatar], [loginUserInfo.value.avatar])
        }
        if (loginUserInfo.value?.avatar) {
          imageStore.ensureThumbBlobUrl(loginUserInfo.value.avatar).then(() => { })
        }
      }
    } catch (e) {
      // 静默失败，不影响 token 同步
      console.error('[ERROR] [userStore] Failed to fetch login user info:', e)
    }
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

    // =========================
    // 3.3 Token 管理
    // =========================
    getAccessToken,
    hasToken,

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
