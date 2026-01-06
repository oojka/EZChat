/**
 * useJoinChat.ts
 * * 职责：
 * 聚合管理加入聊天室的所有逻辑，作为一个统一的入口 (Facade)。
 * * 包含功能模块：
 * 1. 表单状态管理 (Form State)
 * 2. 数据验证与规则 (Validation)
 * 3. 房间信息管理 (Room Info)
 * 4. 本地持久化存储 (LocalStorage)
 * 5. API 交互与路由跳转 (API & Routing)
 * 6. UI 对话框控制 (Dialog Control)
 */

import { ref, reactive, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { type PasswordOptions, REGEX_CHAT_CODE, REGEX_INVITE_URL, isValidNickname, isValidChatCode, isValidPassword, getPasswordReg, isValidInviteUrl, parseAndValidateJoinInfo } from '@/utils/validators'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes'

// API & Stores
import { useUserStore } from '@/stores/userStore'
import { useImageStore } from '@/stores/imageStore'
import { useAppStore } from '@/stores/appStore'
import { useRoomStore } from '@/stores/roomStore'

// Types
import type { LoginForm, JoinChatCredentialsForm, GuestJoinReq, JoinChatReq, ValidateChatJoinReq, Image } from '@/type'

export const useJoinChat = () => {
  // #region 1. Hooks & Stores Setup (基础依赖)
  const router = useRouter()
  const route = useRoute()
  const { t } = useI18n()

  const userStore = useUserStore()
  const imageStore = useImageStore()
  const appStore = useAppStore()
  const roomStore = useRoomStore()

  // #endregion

  // #region 2. State Definitions (状态定义)

  /** * 主流程表单状态
   * 用于：首页加入区域对邀请信息(type: ValidateChatJoinReq)进行验证、路由跳转携带参数
   */
  const joinChatCredentialsForm = ref<JoinChatCredentialsForm>({
    joinMode: 'roomId/password',
    chatCode: '',
    password: '',
    inviteUrl: '',
    inviteCode: '',
  })


  /** 对话框表单 Ref (用于 Element Plus 验证) */
  const joinChatCredentialsFormRef = ref()

  /** * 访客信息状态
   * 用于：访客加入页面填写昵称和头像
   */
  const guestNickname = ref('')
  const guestAvatar = ref<Image>({
    objectThumbUrl: '',
    objectUrl: '',
    objectName: '',
    blobUrl: '',
    blobThumbUrl: '',
  })

  /** 默认头像 URL (当用户未上传时显示) */
  const defaultAvatarUrl = ref('')

  /** UI 交互状态 */
  const isLoading = ref(false)     // 全局加载中
  const isValidating = ref(false)  // 验证中


  // #endregion

  // #region 3. Computed Properties & Rules (计算属性与规则)

  const isRoomIdPasswordMode = computed(() => {
    return joinChatCredentialsForm.value.joinMode === 'roomId/password'
  })
  const isInviteUrlMode = computed(() => {
    return joinChatCredentialsForm.value.joinMode === 'inviteUrl'
  })

  /** * 包装 joinMode 以支持 v-model 双向绑定 
   * 并在切换时处理副作用(如果需要)
   */
  const joinMode = computed({
    get: () => joinChatCredentialsForm.value.joinMode,
    set: (val: 'roomId/password' | 'inviteUrl') => {
      joinChatCredentialsForm.value.joinMode = val
      // 切换模式时重置表单是个好习惯，防止数据混淆
      resetJoinForm()
    }
  })

  /** Element Plus 表单验证规则 */
  const joinChatCredentialsFormRules = computed(() => {
    const rules: any = {}
    if (isRoomIdPasswordMode.value) {
      // ID + 密码模式
      rules.chatCode = [
        // 请输入房间号。
        { required: true, message: t('validation.chat_code_required') || 'Room ID is required', trigger: 'blur' },
        // 不合法的房间号格式。
        { pattern: REGEX_CHAT_CODE, message: t('validation.chat_code_format') || 'Invalid Room ID format', trigger: 'blur' }
      ]
      rules.password = [
        // 请输入密码。
        { required: true, message: t('validation.password_required') || 'Password is required', trigger: 'blur' },
        {
          pattern: getPasswordReg({ min: 8, max: 20, level: 'basic' }),
          // 请输入 8-20 位半角英数字及特殊符号。
          message: t('validation.password_format') || 'Invalid password format',
          trigger: 'blur'
        }
      ]
    } else {
      // 邀请链接模式
      rules.inviteUrl = [
        // 请输入邀请链接。
        { required: true, message: t('validation.invite_url_required') || 'Invite URL is required', trigger: 'blur' },
        // 不合法的邀请链接格式。
        { pattern: REGEX_INVITE_URL, message: t('validation.invite_url_format') || 'Invalid Invite URL format', trigger: 'blur' }
      ]
    }

    return rules
  })

  // #endregion

  // #region 4. Helper Methods (辅助方法)

  /** 重置主表单数据 */
  const resetJoinForm = () => {
    joinChatCredentialsForm.value = {
      joinMode: 'roomId/password', // 默认回退到密码模式
      chatCode: '',
      password: '',
      inviteUrl: '',
      inviteCode: '',
    }
  }

  /** 重置访客信息 */
  const resetGuestForm = () => {
    guestNickname.value = ''
    guestAvatar.value = {
      objectThumbUrl: '',
      objectUrl: '',
      objectName: '',
      blobUrl: '',
      blobThumbUrl: '',
    }
  }

  /** 手动切换加入模式 */
  const toggleJoinMode = () => {
    joinChatCredentialsForm.value.joinMode = joinChatCredentialsForm.value.joinMode === 'roomId/password' ? 'inviteUrl' : 'roomId/password'
    resetJoinForm()
  }

  /**
   * 构建并验证 API 请求 Payload
   * @throws Error 如果验证失败，直接抛出错误信息
   */
  const buildValidatePayload = (): ValidateChatJoinReq => {
    try {
      return parseAndValidateJoinInfo(joinChatCredentialsForm.value)
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'UNKNOWN_VALIDATION_ERROR'
      
      // 映射错误代码到对应的 i18n 翻译
      let message: string
      let errorType = ErrorType.VALIDATION
      
      switch (errorMessage) {
        case 'ROOM_ID_REQUIRED':
          message = 'Room ID is required'
          break
        case 'INVALID_ROOM_ID_FORMAT':
          message = 'Invalid Room ID format'
          break
        case 'PASSWORD_REQUIRED':
          message = 'Password is required'
          break
        case 'INVITE_URL_REQUIRED':
          message = t('validation.invite_url_required') || 'Invite URL is required'
          break
        case 'INVALID_INVITE_URL_FORMAT':
          message = 'Invalid Invite URL format'
          break
        default:
          message = 'Validation failed'
          errorType = ErrorType.UNKNOWN
      }
      
      throw createAppError(
        errorType,
        message,
        {
          severity: ErrorSeverity.ERROR,
          component: 'useJoinChat',
          action: 'buildValidatePayload',
          originalError: error instanceof Error ? error : undefined
        }
      )
    }
  }

  // #endregion

  // #region 6. Room Info Logic (房间信息逻辑)


  /** 初始化默认头像（随机生成或获取固定图） */
  const initDefaultAvatarUrl = () => {
    defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
  }

  // #endregion

  // #region 7. Async Operations (核心业务逻辑)

  /**
   * 第一步：验证房间信息
   * 1. 校验表单
   * 2. 调用 store 验证 API
   * 3. 成功则跳转到 /Join/:id
   */
  const handleValidate = async (): Promise<boolean> => {
    return appStore.runWithLoading(
      t('common.processing') || 'Processing...',
      async () => {
        isValidating.value = true
        userStore.clearValidatedJoinChatInfo() // 先清除旧数据

        try {
          // 获取校验后的请求参数
          const req = buildValidatePayload()

          // 调用 store 验证 API
          const roomData = await roomStore.validateRoomAccess(req)

          if (router.currentRoute.value.path === '/chat') return true

          // 如果当前路径是首页，则跳转到加入页面
          if (router.currentRoute.value.path === '/') {
            router.push(`/Join/${roomData.chatCode}`)
            return true
          }

          // 路由错误，抛出异常终止加入流程
          throw createAppError(
            ErrorType.ROUTER,
            t('chat.router_error') || 'Router error',
            {
              severity: ErrorSeverity.WARNING,
              component: 'useJoinChat',
              action: 'handleValidate'
            }
          )
        } catch (e) {
          // 如果是 AppError，则直接抛出
          if (isAppError(e)) {
            throw e
          }

          // 未知请求错误，抛出异常终止加入流程
          throw createAppError(
            ErrorType.NETWORK,
            t('error.unknown_error'),
            {
              severity: ErrorSeverity.ERROR,
              component: 'useJoinChat',
              action: 'handleValidate',
              originalError: e
            }
          )
        } finally {
          isValidating.value = false
        }
      }
    )
  }

  /**
   * 场景A：访客加入逻辑
   * 1. 二次验证房间号/密码一致性
   * 2. 校验昵称头像
   * 3. 处理默认头像上传
   * 4. 调用 store 执行访客加入
   */
  const handleGuestJoin = async (): Promise<boolean> => {
    try {
        // 0. 类型安全获取路由参数
        const currentChatCode = Array.isArray(route.params.chatCode)
          ? route.params.chatCode[0]
          : route.params.chatCode;

        // 1. 验证房间号是否存在
        if (!currentChatCode) {
          throw createAppError(ErrorType.VALIDATION, 'Room ID is missing', {
            severity: ErrorSeverity.WARNING,
            component: 'useJoinChat',
            action: 'handleGuestJoin'
          })
        }

        if (!isValidChatCode(currentChatCode)) {
          throw createAppError(ErrorType.VALIDATION, 'Invalid Room ID format', {
            severity: ErrorSeverity.WARNING, // 校验失败通常用 WARNING 即可
            component: 'useJoinChat',
            action: 'handleGuestJoin'
          })
        }

        // 验证一致性：确保 URL 的房间号与之前 validate 接口返回的一致
        if (currentChatCode !== userStore.validatedChatRoom?.chatCode) {
          throw createAppError(ErrorType.VALIDATION, 'Room code mismatch', {
            severity: ErrorSeverity.WARNING,
            component: 'useJoinChat',
            action: 'handleGuestJoin'
          })
        }

        // 2. 验证昵称
        if (!isValidNickname(guestNickname.value)) {
          throw createAppError(ErrorType.VALIDATION, 'Invalid Nickname format', {
            severity: ErrorSeverity.WARNING,
            component: 'useJoinChat',
            action: 'handleGuestJoin'
          })
        }

        // 3. 头像处理：如果是默认头像且未上传，先上传
        if (!guestAvatar.value.objectUrl && !guestAvatar.value.objectThumbUrl) {
          try {
            guestAvatar.value = await imageStore.uploadDefaultAvatarIfNeeded(guestAvatar.value, 'user')
          } catch (e) {
            // 如果是 AppError，则直接抛出
            if (isAppError(e)) {
              throw e
            }

            // 未知请求错误，抛出异常终止加入流程
            throw createAppError(
              ErrorType.NETWORK,
              'Avatar upload failed',
              {
                severity: ErrorSeverity.WARNING,
                component: 'useJoinChat',
                action: 'handleGuestJoin',
                originalError: e as Error,
              }
            )
          }
        }

        // 4. 获取并验证凭证，并在 initializeApp 重置状态之前保存 targetCode
        const validatedReq = userStore.validateChatJoinReq
        if (!validatedReq) {
          throw createAppError(
            ErrorType.VALIDATION,
            'Validation info expired, please restart',
            {
              severity: ErrorSeverity.ERROR,
              component: 'useJoinChat',
              action: 'handleGuestJoin'
            })
        }

        // 在 initializeApp 重置状态之前，先保存 targetCode
        const targetCode = 'chatCode' in validatedReq ? validatedReq.chatCode : currentChatCode
        if (!targetCode) {
          throw createAppError(
            ErrorType.VALIDATION,
            'Target chat code is missing',
            {
              severity: ErrorSeverity.ERROR,
              component: 'useJoinChat',
              action: 'handleGuestJoin'
            }
          )
        }

        // 5. 构建请求 (使用类型断言或守卫简化逻辑)
        let req: GuestJoinReq | null = null;
        if ('password' in validatedReq) { // 利用 TS 的 'in' 关键字区分联合类型
          // 类型守卫确保这是密码模式，chatCode 和 password 都是 string
          const passwordReq = validatedReq as { chatCode: string; password: string }
          req = {
            nickName: guestNickname.value,
            avatar: guestAvatar.value,
            chatCode: passwordReq.chatCode,
            password: passwordReq.password,
          }
        } else if ('inviteCode' in validatedReq) {
          // 类型守卫确保这是邀请码模式，inviteCode 是 string
          const inviteReq = validatedReq as { inviteCode: string }
          req = {
            nickName: guestNickname.value,
            avatar: guestAvatar.value,
            inviteCode: inviteReq.inviteCode,
          }
        }

        if (!req) {
          throw createAppError(ErrorType.VALIDATION, 'Invalid validation info', {
            severity: ErrorSeverity.ERROR,
            component: 'useJoinChat',
            action: 'handleGuestJoin'
          })
        }

        // 6. 调用 store 执行访客加入（会调用 initializeApp，重置部分状态）
        const success = await userStore.executeGuestJoin(req, currentChatCode)

        if (success) {
          // 7. 等待 initializeApp 的异步操作完全完成
        
          // 8. 使用之前保存的 targetCode 跳转到聊天室
          await router.push(`/chat/${targetCode}`)
          return true
        }

        return false
    } catch (e) {
      // 如果是 AppError，则直接抛出
      if (isAppError(e)) {
        throw e
      }
      // 未知错误，抛出异常
      throw createAppError(
        ErrorType.NETWORK,
        t('error.unknown_error'),
        {
          severity: ErrorSeverity.ERROR,
          component: 'useJoinChat',
          action: 'handleGuestJoin',
          originalError: e
        }
      )
    }
  }

  /**
   * 场景B：登录用户加入逻辑
   * 1. 执行登录并加入
   * 2. 跳转聊天室
   */
  const handleLoginJoin = async (loginForm: LoginForm): Promise<boolean> => {
    try {
      // 1. 验证一致性
      if (!userStore.validatedChatRoom) {
        throw createAppError(
          ErrorType.VALIDATION,
          'Validation info lost, please retry',
          {
            severity: ErrorSeverity.ERROR,
            component: 'useJoinChat',
            action: 'handleLoginJoin'
          }
        )
      }

      // 2. 在 initializeApp 重置状态之前，先保存目标聊天室代码和加入凭证
      const validatedReq = userStore.validateChatJoinReq
      
      if (!validatedReq) {
        throw createAppError(
          ErrorType.VALIDATION,
          'Validation info expired, please restart',
          {
            severity: ErrorSeverity.ERROR,
            component: 'useJoinChat',
            action: 'handleLoginJoin'
          }
        )
      }

      // 3. 执行登录
      const loginUser = await userStore.loginRequest(loginForm.username, loginForm.password)
      
      if (!loginUser) {
        return false
      }

      // 4. 构建加入请求（利用联合类型的类型守卫）
      let joinReq: JoinChatReq
      
      if ('inviteCode' in validatedReq) {
        // 邀请码模式（使用类型断言明确类型）
        const inviteReq = validatedReq as { inviteCode: string }
        joinReq = { inviteCode: inviteReq.inviteCode }
      } else {
        // 密码模式（使用类型断言明确类型）
        const passwordReq = validatedReq as { chatCode: string; password: string }
        joinReq = { chatCode: passwordReq.chatCode, password: passwordReq.password }
      }

      // 5. 加入聊天室
      const joinResult = await roomStore.joinChat(joinReq)
      
      if (joinResult) {
        // 6. 初始化应用状态（会自己管理加载状态）
        await router.push(`/chat/${userStore.validatedChatRoom.chatCode}` || '/chat')
        await appStore.initializeApp(userStore.getAccessToken(), 'login')
        
        
        return true
      }
      
      return false
    } catch (e) {
      // 如果是 AppError，则直接抛出
      if (isAppError(e)) {
        throw e
      }
      // 未知错误，抛出异常
      throw createAppError(
        ErrorType.NETWORK,
        t('error.unknown_error'),
        {
          severity: ErrorSeverity.ERROR,
          component: 'useJoinChat',
          action: 'handleLoginJoin',
          originalError: e
        }
      )
    }
  }

  /** 对话框步骤状态 (1: 表单, 2: 结果) */
  const joinStep = ref<1 | 2>(1)

  /** 加入结果状态 */
  const joinResult = ref<{ success: boolean; message: string }>({
    success: false,
    message: ''
  })

  const closeJoinDialog = () => {
    roomStore.joinChatDialogVisible = false
    // 重置步骤和结果
    joinStep.value = 1
    joinResult.value = { success: false, message: '' }
  }

  /**
   * 场景C：正式用户ChatView内加入
   * 1. 验证表单
   * 2. 检查是否登录
   * 3. 调用加入 API
   * 4. 设置结果并切换到步骤2
   */
  const handleDialogJoinChat = async (): Promise<boolean> => {
    if (!userStore.validateChatJoinReq || !userStore.validatedChatRoom) return false
    
    return appStore.runWithLoading(
      t('common.processing') || 'Processing...',
      async () => {
        try {
          await joinChatCredentialsFormRef.value.validate()
          // 必须是登录状态（优先恢复正式用户，失败则恢复访客）
          if (!userStore.hasToken()) {
            userStore.restoreLoginUserFromStorage() || userStore.restoreLoginGuestFromStorage()
            if (!userStore.hasToken()) {
              joinResult.value = {
                success: false,
                message: t('auth.login_required') || 'Please login first'
              }
              joinStep.value = 2
              return false
            }
          }

          const req: JoinChatReq = joinChatCredentialsForm.value.joinMode === 'roomId/password'
            ? { chatCode: joinChatCredentialsForm.value.chatCode, password: joinChatCredentialsForm.value.password }
            : { inviteCode: joinChatCredentialsForm.value.inviteCode }

          // 调用封装方法（加入成功后会自动刷新房间列表）
          await roomStore.joinChat(req)

          // 设置成功结果并切换到步骤2
          joinResult.value = {
            success: true,
            message: t('chat.join_success') || 'Joined room successfully'
          }
          joinStep.value = 2

          return true
        } catch (e: unknown) {
          // 设置失败结果并切换到步骤2
          const errorMessage = isAppError(e) 
            ? e.message 
            : e instanceof Error 
              ? e.message 
              : t('common.error') || 'Operation failed'
          
          joinResult.value = {
            success: false,
            message: errorMessage
          }
          joinStep.value = 2
          return false
        }
      }
    )
  }

  /**
   * 处理结果确认
   * 成功：跳转到聊天室并关闭对话框
   * 失败：返回步骤1重新填写
   */
  const handleResultConfirm = async () => {
    if (joinResult.value.success) {
      // 成功：跳转到聊天室（优先使用 validatedChatRoom 中的 chatCode，因为邀请码模式下表单可能没有 chatCode）
      const targetCode = userStore.validatedChatRoom?.chatCode || joinChatCredentialsForm.value.chatCode
      if (targetCode) {
        await router.push(`/chat/${targetCode}`)
      } else {
        await router.push('/chat')
      }
      closeJoinDialog()
    } else {
      // 失败：返回步骤1
      joinStep.value = 1
    }
  }

  /** 别名：处理对话框提交 (Enter键支持) */
  const handleDialogSubmit = handleDialogJoinChat

  // #endregion

  // #region 8. Dialog Control & Events (UI控制)

  /** 头像上传成功回调 */
  const handleAvatarSuccess = (response: any) => {
    if (response?.data) {
      guestAvatar.value.objectThumbUrl = response.data.objectThumbUrl || response.data.url || ''
      guestAvatar.value.objectUrl = response.data.objectUrl || response.data.url || ''
    }
  }

  // #endregion

  // 监听弹窗打开/关闭，实现自动重置表单
  // - 打开时（true）：立即清空表单，确保每次打开都是干净状态
  // - 关闭时（false）：延迟一点等关闭动画完成后再重置（避免动画期间闪烁）
  watch(() => roomStore.joinChatDialogVisible, (newVal) => {
    if (newVal) {
      // 打开时立即清空表单
      resetJoinForm()
    } else {
      // 关闭时延迟重置（避免动画闪烁）
      setTimeout(() => {
        resetJoinForm()
      }, 300)
    }
  })

  return {
    // === State (状态) ===
    joinChatCredentialsForm,       // 验证加入表单
    guestNickname,      // 访客昵称
    guestAvatar,        // 访客头像
    defaultAvatarUrl,   // 默认头像

    // === UI State (界面状态) ===
    isLoading,             // 加入中状态
    isValidating,         // 验证中状态

    // === Computed (计算属性) ===
    isRoomIdPasswordMode,
    isInviteUrlMode,
    joinMode, // 读写 Computed

    // === Config (配置) ===
    joinChatCredentialsFormRules,
    joinChatCredentialsFormRef, // 表单 Ref（用于 Element Plus 验证）

    // === Actions: Core Operations (核心操作) ===
    handleValidate,       // 首页验证
    handleGuestJoin,      // 访客加入
    handleLoginJoin,      // 登录加入
    handleDialogJoinChat, // 弹窗加入
    handleDialogSubmit,   // 弹窗提交快捷方式
    handleJoin: handleDialogJoinChat, // 弹窗加入别名（用于 JoinChatDialog）
    handleResultConfirm,  // 结果确认处理

    // === Actions: Helpers (辅助操作) ===
    resetJoinForm,
    resetGuestForm,
    toggleJoinMode,
    closeJoinDialog,   // 关闭对话框
    handleAvatarSuccess,

    // === Init Methods (初始化方法) ===
    initDefaultAvatarUrl,

    // === Dialog State (对话框状态) ===
    joinStep,
    joinResult,
  }
}