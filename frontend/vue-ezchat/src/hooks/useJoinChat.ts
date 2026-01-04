import {ref} from 'vue'
import {useRouter, useRoute} from 'vue-router'
import {ElMessage} from 'element-plus'
import {useI18n} from 'vue-i18n'
import {validateChatJoinApi} from '@/api/Auth.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {useImageStore} from '@/stores/imageStore'
import useLogin from '@/hooks/useLogin.ts'
import {useAppStore} from '@/stores/appStore.ts'
import type {ValidateChatJoinReq, ChatRoom, Image} from '@/type'

export const useJoinChat = () => {
  const router = useRouter()
  const route = useRoute()
  const {t} = useI18n()
  const userStore = useUserStore()
  const imageStore = useImageStore()
  const appStore = useAppStore()
  
  // 使用 useLogin hook 获取登录功能
  const loginHook = useLogin()
  const { executeLogin } = loginHook

  const joinChatForm = ref<{
    inviteUrl: string
    chatCode: string
    password: string
    joinMode: string
  }>({
    inviteUrl: '',
    chatCode: '',
    password: '',
    joinMode: 'id',
  })

  const isValidating = ref(false)
  const validationError = ref<string>('')

  // Guest form state
  const guestNickname = ref('')
  const guestAvatar = ref<Image>({ objectThumbUrl: '', objectUrl: '', objectName: '', blobUrl: '', blobThumbUrl: '' })
  
  // Loading state
  const isLoading = ref(false)

  // Room info state (from validatedChatRoom)
  const roomInfo = ref({
    chatCode: '',
    chatName: '',
    memberCount: 0,
    avatar: {
      objectThumbUrl: '',
      objectUrl: '',
    },
  })

  // Default avatar URL for display (not uploaded)
  const defaultAvatarUrl = ref('')

  /**
   * 重置加入房间表单
   */
  const resetJoinForm = () => {
    joinChatForm.value = {
      inviteUrl: '',
      chatCode: '',
      password: '',
      joinMode: 'id',
    }
    validationError.value = ''
    isValidating.value = false
  }

  /**
   * 验证聊天室加入请求
   * <p>
   * 业务目的：
   * - 在用户提交加入表单前进行预验证
   * - 验证房间是否存在、密码是否正确、是否允许加入
   * - 验证成功后存储数据并导航到 /join/${chatCode} 页面
   *
   * @returns Promise<boolean> 验证成功返回 true，失败返回 false
   */
  const handleValidate = async (): Promise<boolean> => {
    isValidating.value = true
    validationError.value = ''

    // 清除之前的验证数据，防止使用过期数据
    userStore.clearValidatedChatInfo()

    try {
      // 前端表单验证
      if (joinChatForm.value.joinMode === 'id') {
        // 模式1：ChatCode + Password 模式
        if (!joinChatForm.value.chatCode || joinChatForm.value.chatCode.trim() === '') {
          ElMessage.error(t('validation.room_name_required') || '房间ID不能为空')
          return false
        }
        // 检查 chatCode 格式（8位数字）
        if (!/^[0-9]{8}$/.test(joinChatForm.value.chatCode.trim())) {
          ElMessage.error('房间ID格式错误，应为8位数字')
          return false
        }
        // 检查 password 是否提供（提供 chatCode 时 password 必填）
        if (!joinChatForm.value.password || joinChatForm.value.password.trim() === '') {
          ElMessage.error(t('validation.password_required') || '密码不能为空')
          return false
        }
      } else {
        // 模式2：Invite URL 模式
        if (!joinChatForm.value.inviteUrl || joinChatForm.value.inviteUrl.trim() === '') {
          ElMessage.error('邀请链接不能为空')
          return false
        }
        // 解析邀请 URL，提取 inviteCode
        // 格式：https://ez-chat.oojka.com/invite/{inviteCode}
        const urlPattern = /^https:\/\/ez-chat\.oojka\.com\/invite\/([0-9A-Za-z]{16,24})$/
        const match = joinChatForm.value.inviteUrl.trim().match(urlPattern)
        if (!match) {
          ElMessage.error('邀请链接格式错误，请检查链接是否正确')
          return false
        }
        // 将提取的 inviteCode 存储到表单中（用于后续请求）
        // 注意：这里不直接修改 joinChatForm，而是在创建请求对象时使用
      }

      // 创建验证请求对象
      let req: ValidateChatJoinReq
      if (joinChatForm.value.joinMode === 'id') {
        // 模式1：chatCode + password
        req = {
          chatCode: joinChatForm.value.chatCode.trim(),
          password: joinChatForm.value.password,
        }
      } else {
        // 模式2：inviteCode（从 URL 中提取）
        const urlPattern = /^https:\/\/ez-chat\.oojka\.com\/invite\/([0-9A-Za-z]{16,24})$/
        const match = joinChatForm.value.inviteUrl.trim().match(urlPattern)
        if (!match) {
          ElMessage.error('邀请链接格式错误')
          return false
        }
        req = {
          inviteCode: match[1] as string,
        }
      }

      // 调用验证 API
      const result = await validateChatJoinApi(req)

      if (result && result.data) {
        // 验证成功：存储请求和响应数据
        userStore.setValidatedChatInfo(req, result.data)

        // 导航到 /join/${chatCode} 页面
        const chatCode = result.data.chatCode
        if (chatCode) {
          router.push(`/Join/${chatCode}`)
          return true
        } else {
          ElMessage.error('验证成功但未获取到房间代码')
          return false
        }
      }

      return false
    } catch (error: unknown) {
      // 错误处理和路由跳转已由 request.ts 拦截器统一处理
      // 这里只需要标记验证失败即可
      // 如果需要，可以设置 validationError.value 用于 UI 显示
      if (error instanceof Error) {
        validationError.value = error.message
      }
      return false
    } finally {
      isValidating.value = false
    }
  }

  // 提交逻辑（调用验证）
  const handleJoin = async () => {
    await handleValidate()
  }

  /**
   * 头像上传成功处理
   */
  const handleAvatarSuccess = (response: unknown) => {
    if (response && typeof response === 'object' && 'data' in response) {
      const data = (response as { data: { objectThumbUrl?: string; objectUrl?: string; url?: string } }).data
      if (data) {
        guestAvatar.value.objectThumbUrl = data.objectThumbUrl || data.url || ''
        guestAvatar.value.objectUrl = data.objectUrl || data.url || ''
      }
    }
  }

  /**
   * 访客加入聊天室
   * <p>
   * 业务目的：
   * - 在验证成功后，以访客身份加入聊天室
   * - 上传默认头像（如果未提供）
   * - 调用后端 API 完成加入
   *
   * @returns Promise<boolean> 加入成功返回 true，失败返回 false
   */
  const handleGuestJoin = async (): Promise<boolean> => {
    isLoading.value = true
    try {
      // 获取路由参数中的 chatCode
      const chatCode = route.params.chatCode as string
      if (!chatCode) {
        ElMessage.error('房间代码不能为空')
        return false
      }

      // 获取密码（从验证请求中）
      const password = userStore.validateChatJoinReq?.password || ''

      // 验证昵称
      if (!guestNickname.value || guestNickname.value.trim() === '') {
        ElMessage.error(t('validation.nickname_required') || '昵称不能为空')
        return false
      }

      // 如果用户未上传头像，上传默认头像
      if (!guestAvatar.value.objectUrl && !guestAvatar.value.objectThumbUrl) {
        guestAvatar.value = await imageStore.uploadDefaultAvatarIfNeeded(guestAvatar.value, 'user')
      }

      // TODO: Backend joinApi not implemented yet
      // Call guestApi(chatCode, password, guestNickname.value)
      // On success: save token to localStorage, navigate to '/chat'
      
      return false
    } catch (error: unknown) {
      if (error instanceof Error) {
        ElMessage.error(error.message || '加入失败')
      } else {
        ElMessage.error('加入失败')
      }
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 登录并加入聊天室
   * <p>
   * 业务目的：
   * - 用户登录后自动加入已验证的聊天室
   * - 调用登录 API 获取 token
   * - 登录成功后初始化应用并导航到聊天页
   *
   * @param username 用户名
   * @param password 密码
   * @returns Promise<boolean> 登录并加入成功返回 true，失败返回 false
   */
  const handleLoginJoin = async (username: string, password: string): Promise<boolean> => {
    isLoading.value = true
    try {
      // 调用登录逻辑
      const result = await executeLogin(username, password)
      
      if (result.status !== 1 || !result.data) {
        ElMessage.error(result.message || '登录失败')
        return false
      }

      // 登录成功，初始化应用
      await appStore.initializeApp(result.data.token, 'login')
      
      // TODO: Backend joinApi not implemented yet
      // After login success, should join the validated room
      // const validatedRoom = userStore.validatedChatRoom
      // if (validatedRoom) {
      //   Call join API for the validated room
      // }
      
      // 导航到聊天页
      await router.push('/chat')
      return true
    } catch (error: unknown) {
      if (error instanceof Error) {
        ElMessage.error(error.message || '登录失败')
      } else {
        ElMessage.error('登录失败')
      }
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 重置访客表单
   */
  const resetGuestForm = () => {
    guestNickname.value = ''
    guestAvatar.value = { objectThumbUrl: '', objectUrl: '', objectName: '', blobUrl: '', blobThumbUrl: '' }
  }

  /**
   * 初始化房间信息
   * <p>
   * 业务目的：
   * - 从 userStore.validatedChatRoom 获取验证过的房间信息
   * - 初始化房间信息用于 UI 展示
   */
  const initRoomInfo = () => {
    const validatedRoom = userStore.validatedChatRoom
    if (validatedRoom && validatedRoom.chatCode && validatedRoom.chatName && validatedRoom.memberCount && validatedRoom.avatar && (validatedRoom.avatar.objectThumbUrl || validatedRoom.avatar.objectUrl)) {
      roomInfo.value = {
        chatCode: validatedRoom.chatCode || '',
        chatName: validatedRoom.chatName || '',
        memberCount: validatedRoom.memberCount || 0,
        avatar: validatedRoom.avatar || { objectThumbUrl: '', objectUrl: '' },
      }
      // 初始化房间信息后，清除 userStore 中的验证数据，避免重复使用
      userStore.clearValidatedChatInfo()
    } else {
      // 如果验证过的房间信息不存在，跳转到404页面
      // router.replace('NotFound').catch(() => { }); //TODO: 暂时注释掉，开发完成后取消注释
    }
  }

  /**
   * 初始化默认头像 URL
   * <p>
   * 业务目的：
   * - 生成默认头像 URL 用于展示（不上传）
   */
  const initDefaultAvatarUrl = () => {
    defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
  }

  return {
    joinChatForm,
    handleJoin,
    handleValidate,
    resetJoinForm,
    isValidating,
    validationError,
    // Guest form
    guestNickname,
    guestAvatar,
    handleGuestJoin,
    handleAvatarSuccess,
    resetGuestForm,
    // Login join
    handleLoginJoin,
    // Loading state
    isLoading,
    // Room info
    roomInfo,
    defaultAvatarUrl,
    initRoomInfo,
    initDefaultAvatarUrl,
  }
}
