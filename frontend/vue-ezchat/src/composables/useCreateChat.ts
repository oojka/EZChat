import { computed, reactive, ref, watch } from 'vue'
import type {Image} from '@/type'
import {ElMessage, type FormInstance, type FormRules, type UploadProps, type UploadFile} from 'element-plus'
import { useI18n } from 'vue-i18n'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import { calculateObjectHash } from '@/utils/objectHash'
import { isImage } from '@/utils/validators'
import { checkObjectExistsApi } from '@/api/Media'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'
import { createChatApi, getChatRoomApi } from '@/api/Chat'
import { useRoomStore } from '@/stores/roomStore'
import { useRouter } from 'vue-router'
import { useImageStore } from '@/stores/imageStore'

export const useCreateChat = () => {
  const roomStore = useRoomStore()
  const router = useRouter()
  const { t } = useI18n()
  const imageStore = useImageStore()

  // ============================================================
  // Type Guards（类型守卫函数）
  // ============================================================

  /**
   * 类型守卫：检查值是否为字符串
   */
  const isString = (value: unknown): value is string => {
    return typeof value === 'string'
  }

  /**
   * 类型守卫：检查值是否为 File 对象
   */
  const isFile = (value: unknown): value is File => {
    return value instanceof File
  }
  const createStep = ref<1 | 2 | 3 | 4>(1)
  const createResult = ref<{ success: boolean; message: string; chatCode?: string; inviteCode?: string; inviteUrl?: string }>({ success: false, message: '' })
  const isCreating = ref(false)
  const hasPasswordError = ref(false)
  const passwordErrorMessage = ref('')

  /**
   * i18n 兜底：当 key 缺失时，vue-i18n 默认会返回 key 字符串本身（truthy），
   * 这会导致 `t('xxx') || 'fallback'` 的写法失效，从而把 key 直接渲染到页面。
   */
  const tf = (key: string, fallback: string) => {
    // 使用类型守卫：检查翻译结果是否为字符串
    const translationResult = t(key)
    const res = isString(translationResult) ? translationResult : String(translationResult)
    return res === key ? fallback : res
  }

  const createChatForm = ref<{
    avatar: Image
    chatName: string
    /**
     * 密码保护开关（仅前端）：
     * - 1: 启用密码保护，密码必填
     * - 0: 禁用密码保护，后端 chat_password_hash = NULL
     * 注意：这不是 DB 的 join_enabled（全局加入开关），API 请求固定传 joinEnable: 1
     */
    joinEnableByPassword: 0 | 1
    joinLinkExpiryMinutes: number | null
    oneTimeLink: boolean
    password: string
    passwordConfirm: string
  }>({
    avatar: {
      imageName: '',
      imageUrl: '',
      imageThumbUrl: '',
      blobUrl: '',
      blobThumbUrl: '',
    },
    chatName: '',
    // 密码保护开关：默认开启（1）→ 用户需要设置密码
    joinEnableByPassword: 1,
    joinLinkExpiryMinutes: 10080,
    oneTimeLink: false,
    password: '',
    passwordConfirm: '',
  })

  const selectedDate = ref<Date | null>(null)

  const selectedDateRadio = ref<1 | 7 | 30 | null>(7)

  // 逻辑1：监听单选按钮变化
  watch(selectedDateRadio, (newVal) => {
    if (newVal) {
      // 互斥逻辑：选中预设时间时，清空自定义日期
      selectedDate.value = null
      // 直接赋值给表单
      createChatForm.value.joinLinkExpiryMinutes = newVal * 24 * 60
    }
  })

  // 逻辑2：监听自定义日期变化
  watch(selectedDate, (newVal) => {
    if (newVal) {
      // 互斥逻辑：选中自定义日期时，清空预设单选框
      selectedDateRadio.value = null

      // 计算当前时间与选中时间的差值（毫秒 -> 分钟）
      const diffInfo = newVal.getTime() - Date.now()

      // 确保至少为 1 分钟，避免负数
      createChatForm.value.joinLinkExpiryMinutes = Math.max(1, Math.floor(diffInfo / 60000))
    } else if (!selectedDateRadio.value) {
      // 如果自定义日期被清空且预设也为空，恢复默认 7 天
      selectedDateRadio.value = 7
    }
  })

  // 逻辑3：监听"密码保护开关"变化，关闭时清除密码相关状态
  watch(() => createChatForm.value.joinEnableByPassword, (newVal) => {
    if (newVal === 0) {
      // 关闭密码保护时：清除错误状态和密码字段
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      createChatForm.value.password = ''
      createChatForm.value.passwordConfirm = ''
      // 清除表单验证错误
      if (createFormRef.value) {
        createFormRef.value.clearValidate(['password', 'passwordConfirm'])
      }
    }
  })

  const disabledDate = (time: Date) => {
    const now = new Date()
    const startOfToday = new Date(now.setHours(0, 0, 0, 0))
    const thirtyDaysLater = new Date(startOfToday.getTime() + 31 * 24 * 60 * 60 * 1000)
    return time.getTime() < startOfToday.getTime() || time.getTime() > thirtyDaysLater.getTime()
  }

  const createFormRef = ref<FormInstance>()

  // 1. 头像上传逻辑
  const beforeAvatarUpload: UploadProps['beforeUpload'] = async (rawFile) => {
    // 放宽图片类型限制：允许常见 image/*（并用扩展名兜底）
    // 使用类型守卫：检查 rawFile 是否为 File 对象
    if (!isFile(rawFile)) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    const isValidImage = isAllowedImageFile(rawFile)
    const isLtMaxSize = rawFile.size / 1024 / 1024 < MAX_IMAGE_SIZE_MB

    if (!isValidImage) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    if (!isLtMaxSize) {
      ElMessage.error(t('validation.image_size'))
      return false
    }

    try {
      // 计算原始对象哈希（在压缩之前，确保是真正的原始对象）
      // rawFile 已经通过 isFile 类型守卫验证，TypeScript 会自动收窄类型
      const rawHash = await calculateObjectHash(rawFile)

      // 调用比对接口，检查对象是否已存在
      try {
        const checkResult = await checkObjectExistsApi(rawHash)

        if (checkResult.status === 1 && checkResult.data) {
          // 对象已存在，直接使用返回的 Image 对象
          // 注意：handleAvatarSuccess 期望的参数格式是 { data: Image }，需要适配
          // 同时需要补充 blobUrl 和 blobThumbUrl 字段（如果后端没有返回）
          const imageData: Image = {
            ...checkResult.data,
            blobUrl: checkResult.data.blobUrl || '',
            blobThumbUrl: checkResult.data.blobThumbUrl || ''
          }
          // UploadProps['onSuccess'] 的签名是 (response: unknown, uploadFile?: UploadFile, uploadFiles?: UploadFile[]) => void
          // 定义响应类型，避免使用 any
          type UploadSuccessResponse = {
            data: Image
          }
          const response: UploadSuccessResponse = { data: imageData }
          // UploadProps['onSuccess'] 的签名是 (response: unknown, uploadFile?: UploadFile, uploadFiles?: UploadFile[]) => void
          // 由于参数是可选的，我们只传入必需的 response 参数
          // 注意：这里不使用类型断言，而是依赖 TypeScript 的类型系统
          // 使用函数重载或可选参数的方式调用
          // UploadProps['onSuccess'] 需要 3 个参数，但后两个是可选的
          // 创建一个最小化的 UploadFile 对象以满足类型要求
          const emptyUploadFile: Partial<UploadFile> = {}
          handleAvatarSuccess(response, emptyUploadFile as UploadFile, [])
          // 返回 false 阻止 el-upload 实际上传对象
          return false
        }
      } catch (error) {
        console.error('[ERROR] [beforeAvatarUpload] Failed to check object existence:', error)
        // 比对接口失败，降级为正常上传流程（继续上传）
      }

      // 对象不存在或比对失败，继续正常上传流程
      // 前端压缩：失败则回退原图
      // rawFile 已经通过 isFile 类型守卫验证，TypeScript 会自动收窄类型
      return await compressImage(rawFile)
    } catch (error) {
      console.error('[ERROR] [beforeAvatarUpload] Failed to calculate hash:', error)
      // 哈希计算失败，降级为正常上传流程
      // rawFile 已经通过 isFile 类型守卫验证，TypeScript 会自动收窄类型
      if (isFile(rawFile)) {
        return await compressImage(rawFile)
      }
      // 如果 rawFile 不是 File 对象，返回 false 阻止上传
      return false
    }
  }

  const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    // 使用类型守卫验证 response.data 是否为 Image 类型
    if (response && typeof response === 'object' && 'data' in response) {
      const data = (response as { data: unknown }).data
      if (isImage(data)) {
        createChatForm.value.avatar = data
      } else {
        console.warn('[WARN] [handleAvatarSuccess] Invalid Image data format:', data)
      }
    }
  }

  // 重置表单
  const resetCreateForm = () => {
    createChatForm.value = {
      avatar: {
        imageName: '',
        imageUrl: '',
        imageThumbUrl: '',
        blobUrl: '',
        blobThumbUrl: '',
      },
      chatName: '',
      joinEnableByPassword: 1,
      joinLinkExpiryMinutes: 10080,
      oneTimeLink: false,
      password: '',
      passwordConfirm: '',
    }
    selectedDate.value = null
    selectedDateRadio.value = 7
    createStep.value = 1
    createResult.value = { success: false, message: '' }
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    if (createFormRef.value) {
      createFormRef.value.resetFields()
    }
  }

  /**
   * 弹窗关闭逻辑：
   * - 如果创建成功，先获取新房间信息并插入列表，然后导航到新房间
   * - 否则直接关闭弹窗
   */
  const handleClose = async () => {
    // 如果创建成功，需要先获取新房间信息并插入列表，然后导航
    if (createResult.value.success && createResult.value.chatCode) {
      const chatCode = createResult.value.chatCode
      try {
        // 获取新房间的完整信息（包含成员列表等）
        const result = await getChatRoomApi(chatCode)
        if (result?.data) {
          // 使用 updateRoomInfo 将新房间插入列表（如果不存在则新增）
          roomStore.updateRoomInfo(result.data)
        }
        // 导航到新创建的房间
        router.push(`/chat/${chatCode}`)
      } catch (e) {
        console.error('[ERROR] [useCreateChat] Failed to fetch new room info:', e)
        // 即使获取失败，也导航到新房间（路由会触发 ChatView 的数据加载）
        router.push(`/chat/${chatCode}`)
      }
    }
    // 关闭弹窗
    roomStore.createChatDialogVisible = false
  }

  // 监听弹窗打开/关闭，实现自动重置表单
  // - 打开时（true）：立即清空表单，确保每次打开都是干净状态
  // - 关闭时（false）：延迟一点等关闭动画完成后再重置（避免动画期间闪烁）
  watch(() => roomStore.createChatDialogVisible, (newVal) => {
    if (newVal) {
      // 打开时立即清空表单
      resetCreateForm()
    } else {
      // 关闭时延迟重置（避免动画闪烁）
      setTimeout(() => {
        resetCreateForm()
      }, 300)
    }
  })

  // roomId 展示：8 位数字做分组（更像“邀请码/凭证”）
  const roomIdDisplay = computed(() => {
    const roomId = createResult.value?.chatCode || ''
    return roomId.replace(/^(\d{4})(\d{4})$/, '$1 $2')
  })

  // 复制：邀请链接
  const copyInviteLink = async () => {
    const url = createResult.value?.inviteUrl
    if (!url) return
    try {
      await navigator.clipboard.writeText(url)
      ElMessage.success(tf('common.copied', '已复制'))
    } catch {
      ElMessage.error(tf('common.copy_failed', '复制失败'))
    }
  }

  // 复制：roomId
  const copyRoomId = async () => {
    const roomId = createResult.value?.chatCode
    if (!roomId) return
    try {
      await navigator.clipboard.writeText(roomId)
      ElMessage.success(tf('common.copied', '已复制'))
    } catch {
      ElMessage.error(tf('common.copy_failed', '复制失败'))
    }
  }

  // 2. 自定义密码校验规则（密码保护开启时必填）
  // 注意：Element Plus 的 validator 函数签名需要匹配 InternalRuleItem，使用 unknown 类型避免 any
  const validatePassword = (rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    // 使用类型守卫：检查 value 是否为字符串
    if (!isString(value)) {
      return callback(new Error(t('validation.password_required')))
    }
    const stringValue = value
    // 密码保护关闭：跳过校验
    if (createChatForm.value.joinEnableByPassword === 0) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      return callback()
    }
    // 密码保护开启：密码必填
    if (!stringValue) {
      const errorMsg = t('validation.password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    // 如果填写了确认密码，则触发联动校验
    if (createChatForm.value.passwordConfirm !== '') {
      createFormRef.value?.validateField('passwordConfirm', () => {})
    }
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    return callback()
  }

  const validatePasswordConfirm = (rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    // 使用类型守卫：检查 value 是否为字符串
    if (!isString(value)) {
      return callback(new Error(t('validation.confirm_password_required')))
    }
    const stringValue = value
    // 密码保护关闭：跳过校验
    if (createChatForm.value.joinEnableByPassword === 0) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      return callback()
    }

    // 密码保护开启：确认密码必填
    if (!stringValue) {
      const errorMsg = t('validation.confirm_password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    // 两次密码必须一致
    if (stringValue !== createChatForm.value.password) {
      const errorMsg = t('validation.password_mismatch')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    return callback()
  }

  // 3. 校验规则定义
  const createFormRules = reactive<FormRules>({
    chatName: [
      { required: true, message: t('validation.room_name_required'), trigger: 'blur' },
      { min: 1, max: 20, message: t('validation.room_name_length'), trigger: 'blur' },
    ],
    password: [{ validator: validatePassword, trigger: ['blur', 'change'] }],
    passwordConfirm: [{ validator: validatePasswordConfirm, trigger: ['blur', 'change'] }],
  })

  // 步骤验证
  const validateStep = async (step: number): Promise<boolean> => {
    if (!createFormRef.value) return false
    let fieldsToValidate: string[] = []
    if (step === 1) {
      // Step 1: 验证房间名称
      fieldsToValidate = ['chatName']
    } else if (step === 2) {
      // Step 2: 如果密码保护开启（joinEnableByPassword=1），则校验密码必填且一致
      if (createChatForm.value.joinEnableByPassword === 1) {
        fieldsToValidate = ['password', 'passwordConfirm']
      } else {
        // 密码保护关闭：无需校验，直接通过
        return true
      }
    } else if (step === 3) {
      // Step 3: 过期时间无需验证
      return true
    }
    try {
      await createFormRef.value.validateField(fieldsToValidate)
      return true
    } catch {
      return false
    }
  }

  // 步骤导航
  const nextStep = async () => {
    if (await validateStep(createStep.value) && createStep.value < 3) {
      createStep.value++
    }
  }

  const prevStep = () => {
    if (createStep.value > 1 && createStep.value <= 3) {
      createStep.value--
    }
  }

  // 4. 提交包装函数
  const handleCreate = async () => {
    if (!await validateStep(3)) {
      //
      ElMessage.error(t(''))
      return
    }
    isCreating.value = true
    try {
      await createFormRef.value!.validate()

      // 如果用户未设置头像（Image 对象为空或空串），上传默认头像
      if (!createChatForm.value.avatar.imageUrl && !createChatForm.value.avatar.imageThumbUrl) {
        createChatForm.value.avatar = await imageStore.uploadDefaultAvatarIfNeeded(createChatForm.value.avatar, 'room')
      }

      // API payload:
      // - joinEnable: 固定传 1（对应 DB join_enabled，全局允许加入；本期不做 UI 控制）
      // - password/passwordConfirm: 仅当 joinEnableByPassword=1 时有意义，否则后端不写 hash
      // - maxUses: 一次性链接开关，true=1（一次性），false=0（无限）
      const maxUses = createChatForm.value.oneTimeLink ? 1 : 0
      const result = await createChatApi({
        chatName: createChatForm.value.chatName,
        avatar: createChatForm.value.avatar,
        joinEnable: 1, // 固定值：全局允许加入
        joinLinkExpiryMinutes: createChatForm.value.joinLinkExpiryMinutes,
        maxUses,
        password: createChatForm.value.joinEnableByPassword === 1 ? createChatForm.value.password : '',
        passwordConfirm: createChatForm.value.joinEnableByPassword === 1 ? createChatForm.value.passwordConfirm : '',
      })

      const chatCode = result?.data?.chatCode
      const inviteCode = result?.data?.inviteCode
      const inviteUrl = inviteCode
        ? `https://ez-chat.oojka.com/invite/${inviteCode}`
        : ''

      createResult.value = {
        success: true,
        message: t('create_chat.success_msg') || '创建成功',
        chatCode,
        inviteCode,
        inviteUrl,
      }
      createStep.value = 4
    } catch (error: unknown) {
      // 类型守卫：检查 error 是否为 Error 对象
      // 捕获异常（头像上传失败、API 错误或网络错误）
      console.error('[ERROR] [handleCreate]', error)
      const errorMessage = error instanceof Error ? error.message : t('common.error')
      createResult.value = { success: false, message: errorMessage }
      ElMessage.error(errorMessage)
    } finally {
      isCreating.value = false
    }
  }

  return {
    createChatForm,
    createStep,
    createResult,
    isCreating,
    hasPasswordError,
    passwordErrorMessage,
    handleCreate,
    createFormRef,
    createFormRules,
    beforeAvatarUpload,
    handleAvatarSuccess,
    selectedDate,
    selectedDateRadio,
    disabledDate,
    resetCreateForm,
    validateStep,
    nextStep,
    prevStep,
    // UI 辅助：由 CreateChatDialog 使用
    tf,
    handleClose,
    roomIdDisplay,
    copyInviteLink,
    copyRoomId,
  }
}
