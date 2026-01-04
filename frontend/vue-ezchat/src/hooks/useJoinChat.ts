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

// API & Stores
import { validateChatJoinApi, guestJoinApi } from '@/api/Auth'
import { joinChatApi } from '@/api/Chat'
import { useUserStore } from '@/stores/userStore'
import { useImageStore } from '@/stores/imageStore'
import { useAppStore } from '@/stores/appStore'
import { useRoomStore } from '@/stores/roomStore'
import useLogin from '@/hooks/useLogin'

// Types
import type { JoinChatCredentialsForm, GuestJoinReq, JoinChatReq, ValidateChatJoinReq, Image, RoomInfo } from '@/type'

// #region === Constants (常量定义) ===

/** 聊天室代码正则：8位数字 */
const REGEX_CHAT_CODE = /^[0-9]{8}$/
/** 邀请链接正则：提取末尾的 hash */
const REGEX_INVITE_URL = /^https:\/\/ez-chat\.oojka\.com\/invite\/([0-9A-Za-z]{16,24})$/
/** 本地存储 Keys */
const STORAGE_KEYS = {
  JOIN_CHAT_FORM: 'joinChatForm',
} as const

// #endregion

export const useJoinChat = () => {
  // #region 1. Hooks & Stores Setup (基础依赖)
  const router = useRouter()
  const route = useRoute()
  const { t } = useI18n()
  
  const userStore = useUserStore()
  const imageStore = useImageStore()
  const appStore = useAppStore()
  const roomStore = useRoomStore()
  const { executeLogin } = useLogin()
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
  const joinFormRef = ref()

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

  /** * 房间信息状态
   * 数据来源：验证成功后由后端返回
   */
  const roomInfo = ref<RoomInfo>({
    chatCode: '',
    chatName: '',
    memberCount: 0,
    avatar: { objectThumbUrl: '', objectUrl: '' },
  })
  
  /** 默认头像 URL (当用户未上传时显示) */
  const defaultAvatarUrl = ref('')

  /** UI 交互状态 */
  const isLoading = ref(false)     // 全局加载中
  const isValidating = ref(false)  // 验证中
  

  // #endregion

  // #region 3. Computed Properties & Rules (计算属性与规则)

  const isPasswordMode = computed(() => joinChatForm.value.joinMode === 'roomId/password')
  const isInviteMode = computed(() => joinChatForm.value.joinMode === 'inviteUrl')
  
  /** * 包装 joinMode 以支持 v-model 双向绑定 
   * 并在切换时处理副作用(如果需要)
   */
  const joinMode = computed({
    get: () => joinChatForm.value.joinMode,
    set: (val: 'roomId/password' | 'inviteUrl') => {
      joinChatForm.value.joinMode = val
      // 切换模式时重置表单是个好习惯，防止数据混淆
      resetJoinForm()
    }
  })

  /** Element Plus 表单验证规则 */
  const joinFormRules = {
    chatCode: [
      { required: true, message: t('chat.chat_code_required'), trigger: 'blur' },
      { pattern: REGEX_CHAT_CODE, message: t('chat.chat_code_format'), trigger: 'blur' }
    ],
    password: [
      { required: true, message: t('chat.password_required'), trigger: 'blur' }
    ],
    inviteCode: [
      { required: true, message: t('chat.invite_url_required'), trigger: 'blur' },
      { pattern: /^[0-9A-Za-z]{16,24}$/, message: t('chat.invite_url_format'), trigger: 'blur' }
    ]
  }

  // #endregion

  // #region 4. Helper Methods (辅助方法)

  /** 重置主表单数据 */
  const resetJoinForm = () => {
    joinChatForm.value = {
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
    joinChatForm.value.joinMode = joinChatForm.value.joinMode === 'roomId/password' ? 'inviteUrl' : 'roomId/password'
    resetJoinForm()
  }

  /**
   * 构建并验证 API 请求 Payload
   * @throws Error 如果验证失败，直接抛出错误信息
   */
  const buildValidatePayload = (): ValidateChatJoinReq => {
    const { joinMode, chatCode, password, inviteUrl } = joinChatForm.value

    if (joinMode === 'roomId/password') {
      const code = chatCode?.trim()
      if (!code) throw new Error(t('validation.room_name_required') || 'Room ID is required')
      if (!REGEX_CHAT_CODE.test(code)) throw new Error(t('validation.room_id_format_error') || 'Invalid Room ID format')
      if (!password) throw new Error(t('validation.password_required') || 'Password is required')
      
      return { chatCode: code, password }
    } else {
      const url = inviteUrl?.trim()
      if (!url) throw new Error(t('validation.invite_url_required') || 'Invite URL is required')
      
      const match = url.match(REGEX_INVITE_URL)
      // 确保捕获组(group 1)确实抓到了内容
      if (!match || !match[1]) throw new Error(t('validation.invite_url_format_error') || 'Invalid Invite URL format')
      
      return { inviteCode: match[1] }
    }
  }

  // #endregion

  // #region 5. Storage Logic (本地存储)

  /** 保存表单状态到 localStorage */
  const saveJoinChatFormToStorage = () => {
    try {
      localStorage.setItem(STORAGE_KEYS.JOIN_CHAT_FORM, JSON.stringify(joinChatForm.value))
    } catch (error) {
      console.error('Storage save failed:', error)
    }
  }

  /** 从 localStorage 恢复表单状态 */
  const restoreJoinChatFormFromStorage = () => {
    try {
      const rawString = localStorage.getItem(STORAGE_KEYS.JOIN_CHAT_FORM)
      if (!rawString) return
      const parsed = JSON.parse(rawString)
      // 简单的类型守卫，防止脏数据导致崩溃
      if (parsed && typeof parsed === 'object' && 'joinMode' in parsed) {
        joinChatForm.value = parsed
      }
    } catch (error) {
      console.error('Storage restore failed:', error)
      localStorage.removeItem(STORAGE_KEYS.JOIN_CHAT_FORM)
    }
  }

  /** * 初始化存储逻辑
   * 1. 恢复数据
   * 2. 开启监听，实现自动保存
   */
  const initStorage = () => {
    restoreJoinChatFormFromStorage()
    watch(() => joinChatForm.value, saveJoinChatFormToStorage, { deep: true })
  }

  // #endregion

  // #region 6. Room Info Logic (房间信息逻辑)

  /**
   * 初始化房间信息
   * 场景：从首页验证跳转到加入页后，需要回显房间信息
   */
  const initRoomInfo = () => {
    const validatedRoom = userStore.validatedChatRoom
    if (validatedRoom?.chatCode) {
      roomInfo.value = {
        chatCode: validatedRoom.chatCode,
        chatName: validatedRoom.chatName || '',
        memberCount: validatedRoom.memberCount || 0,
        avatar: validatedRoom.avatar || { objectThumbUrl: '', objectUrl: '' },
      }
      // 初始化后立即清除 store 中的临时数据，防止下次污染
      userStore.clearValidatedChatInfo()
    } else {
      console.warn(t('chat.no_validated_room_info') || 'No validated room info found, user might have accessed URL directly')
    }
  }

  /** 初始化默认头像（随机生成或获取固定图） */
  const initDefaultAvatarUrl = () => {
    defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
  }

  // #endregion

  // #region 7. Async Operations (核心业务逻辑)

  /**
   * 第一步：验证房间信息（在首页调用）
   * 1. 校验表单
   * 2. 调用 validate API
   * 3. 成功则存入 Store 并跳转到 /Join/:id
   */
  const handleValidate = async (): Promise<boolean> => {
    isValidating.value = true
    userStore.clearValidatedChatInfo() // 先清除旧数据
    
    try {
      // 获取校验后的请求参数
      const req = buildValidatePayload()
      const result = await validateChatJoinApi(req)
      
      if (result && result.data && result.data.chatCode) {
        // 验证成功：存储数据并跳转
        userStore.setValidatedChatInfo(req, result.data)
        router.push(`/Join/${result.data.chatCode}`)
        return true
      }
      throw new Error(t('chat.validation_success_but_no_room_code') || 'Validation successful but no room code received')
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : t('chat.validation_failed') || 'Validation failed'
      ElMessage.error(msg)
      return false
    } finally {
      isValidating.value = false
    }
  }

  /**
   * 场景A：访客加入逻辑
   * 1. 二次验证房间号/密码一致性
   * 2. 校验昵称头像
   * 3. 处理默认头像上传
   * 4. 调用 Guest Join API
   */
  const handleGuestJoin = async (): Promise<boolean> => {
    isLoading.value = true
    try {
      const currentChatCode = route.params.chatCode as string
      if (!currentChatCode) throw new Error(t('chat.room_code_required') || 'Room code is required')

      // 安全检查：确保 URL 中的房间号与填写的一致
      const formData = joinChatForm.value
      if (formData.joinMode === 'password' && formData.chatCode !== currentChatCode) {
        throw new Error(t('chat.room_code_mismatch') || 'Room code mismatch, please re-validate')
      }

      // 验证昵称
      if (!guestNickname.value.trim()) throw new Error(t('validation.nickname_required') || 'Nickname is required')

      // 头像处理：如果是默认头像且未上传，先上传
      if (!guestAvatar.value.objectUrl && !guestAvatar.value.objectThumbUrl) {
        guestAvatar.value = await imageStore.uploadDefaultAvatarIfNeeded(guestAvatar.value, 'user')
      }

      // 从 Store 获取之前的验证凭证 (password 或 inviteCode)
      const validatedReq = userStore.validateChatJoinReq
      if (!validatedReq) throw new Error(t('chat.validation_info_expired') || 'Validation info expired, please restart')

      // 构建请求 (根据之前的验证类型)
      const req: GuestJoinReq = {
        nickName: guestNickname.value,
        avatar: guestAvatar.value,
        ...( 'password' in validatedReq 
             ? { chatCode: validatedReq.chatCode, password: validatedReq.password } 
             : { inviteCode: validatedReq.inviteCode } 
           )
      } as GuestJoinReq

      console.log(t('chat.ready_to_send_guest_join_req') || 'Ready to send Guest Join Req:', req)
      
      // TODO: 后端 API 对接点
      // const res = await guestJoinApi(req)
      // if (res.status === 1) {
      //    await appStore.initializeApp(res.data.token, 'guest')
      //    router.push('/chat')
      // }
      
      return false // 暂未实现完整后端，这里返回 false
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('chat.join_failed') || 'Join failed')
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 场景B：登录用户加入逻辑
   * 1. 执行登录
   * 2. 校验房间信息一致性
   * 3. 调用 Auth Join API
   * 4. 跳转聊天室
   */
  const handleLoginJoin = async (username: string, pass: string): Promise<boolean> => {
    isLoading.value = true
    try {
      // 1. 登录
      const loginRes = await executeLogin(username, pass)
      if (loginRes.status !== 1 || !loginRes.data) throw new Error(loginRes.message || 'Login failed')
      await appStore.initializeApp(loginRes.data.token, 'login')

      // 2. 验证一致性
      const validatedRoom = userStore.validatedChatRoom
      if (!validatedRoom?.chatCode) throw new Error(t('chat.validation_info_lost') || 'Validation info lost, please refresh and retry')

      // 3. 构建加入请求
      let joinReq: JoinChatReq
      if (joinChatForm.value.joinMode === 'password') {
        joinReq = { chatCode: joinChatForm.value.chatCode, password: joinChatForm.value.password }
      } else {
        const match = joinChatForm.value.inviteUrl.match(REGEX_INVITE_URL)
        if (!match || !match[1]) throw new Error(t('chat.invalid_invite_url') || 'Invalid invite URL')
        joinReq = { inviteCode: match[1] }
      }

      // 4. 正式加入
      const joinRes = await joinChatApi(joinReq)
      if (joinRes.status !== 1 || !joinRes.data) throw new Error(joinRes.message || t('chat.join_failed') || 'Failed to join room')

      // 5. 更新 Token 并跳转
      await appStore.initializeApp(joinRes.data.token, 'login')
      await router.push(`/chat/${validatedRoom.chatCode}`)
      return true
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('chat.operation_failed') || 'Operation failed')
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 场景C：对话框快速加入 (Sidebar)
   * 1. 验证表单
   * 2. 检查是否登录
   * 3. 调用加入 API
   * 4. 设置结果并切换到步骤2
   */
  const handleDialogJoinChat = async (): Promise<boolean> => {
    if (!joinFormRef.value) return false
    try {
      await joinFormRef.value.validate()
      // 必须是登录状态
      if (!userStore.loginUser?.token) {
        joinResult.value = {
          success: false,
          message: t('auth.login_required') || 'Please login first'
        }
        joinStep.value = 2
        return false
      }

      isLoading.value = true
      
      const req: JoinChatReq = joinChatForm.value.joinMode === 'roomId/password'
        ? { chatCode: joinChatForm.value.chatCode, password: joinChatForm.value.password }
        : { inviteCode: joinChatForm.value.inviteCode }

      const res = await joinChatApi(req)
      if (res.status !== 1 || !res.data) {
        throw new Error(res.message || t('chat.join_failed') || 'Failed to join room')
      }

      // 加入成功：刷新数据
      await appStore.initializeApp(res.data.token, 'login')
      await roomStore.initRoomList()
      
      // 设置成功结果并切换到步骤2
      joinResult.value = {
        success: true,
        message: t('chat.join_success') || 'Joined room successfully'
      }
      joinStep.value = 2
      
      return true
    } catch (error) {
      // 设置失败结果并切换到步骤2
      joinResult.value = {
        success: false,
        message: error instanceof Error ? error.message : t('common.error') || 'Operation failed'
      }
      joinStep.value = 2
      return false
    } finally {
      isLoading.value = false
    }
  }
  
  /**
   * 处理结果确认
   * 成功：跳转到聊天室并关闭对话框
   * 失败：返回步骤1重新填写
   */
  const handleResultConfirm = async () => {
    if (joinResult.value.success) {
      // 成功：跳转到聊天室
      const targetCode = joinForm.chatCode
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

  const closeJoinDialog = () => {
    roomStore.joinChatDialogVisible = false
    // 重置步骤和结果
    joinStep.value = 1
    joinResult.value = { success: false, message: '' }
  }
  
  /** 关闭对话框的别名 */
  const closeDialog = closeJoinDialog

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
      resetDialogForm()
    } else {
      // 关闭时延迟重置（避免动画闪烁）
      setTimeout(() => {
        resetDialogForm()
      }, 300)
    }
  })

  return {
    // === State (状态) ===
    joinChatForm,       // 验证加入表单
    guestNickname,      // 访客昵称
    guestAvatar,        // 访客头像
    roomInfo,           // 房间信息
    defaultAvatarUrl,   // 默认头像
    
    // === UI State (界面状态) ===
    isLoading,             // 加入中状态
    isValidating,         // 验证中状态
    
    // === Computed (计算属性) ===
    isPasswordMode,
    isInviteMode,
    joinMode, // 读写 Computed
    
    // === Config (配置) ===
    joinFormRules,
    joinFormRef,
    
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
    resetDialogForm,
    resetGuestForm,
    toggleJoinMode,
    closeJoinDialog,
    closeDialog,         // 关闭对话框别名（用于 JoinChatDialog）
    handleAvatarSuccess,
    
    // === Init Methods (初始化方法) ===
    initStorage,
    initRoomInfo,
    initDefaultAvatarUrl,
  }
}