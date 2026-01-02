import { reactive, ref, watch } from 'vue'
import type {Image} from '@/type'
import {ElMessage, type FormInstance, type FormRules, type UploadProps, type FormItemRule} from 'element-plus'
import { useI18n } from 'vue-i18n'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'

export const useCreateChat = () => {
  const { t } = useI18n()
  const createStep = ref<1 | 2 | 3 | 4>(1)
  const createResult = ref({ success: false, message: '' })
  const isCreating = ref(false)
  const hasPasswordError = ref(false)
  const passwordErrorMessage = ref('')
  
  const createChatForm = ref<{
    avatar: Image
    chatName: string
    joinEnable: 0 | 1
    joinLinkExpiryMinutes: number | null
    password: string
    passwordConfirm: string
  }>({
    avatar: {
      objectName: '',
      objectUrl: '',
      objectThumbUrl: '',
    },
    chatName: '',
    joinEnable: 0,
    joinLinkExpiryMinutes: 10080,
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

  // 逻辑3：监听密码开关变化，关闭时清除错误状态
  watch(() => createChatForm.value.joinEnable, (newVal) => {
    if (newVal === 0) {
      // 关闭密码时，清除错误状态和密码字段
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
    const isImage = isAllowedImageFile(rawFile as File)
    const isLt2M = rawFile.size / 1024 / 1024 < 2

    if (!isImage) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    if (!isLt2M) {
      ElMessage.error(t('validation.image_size'))
      return false
    }
    // 前端压缩：失败则回退原图
    return await compressImage(rawFile as File)
  }

  const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    if (response && response.data) {
      createChatForm.value.avatar = response.data
      // 触发头像字段验证
      if (createFormRef.value) {
        createFormRef.value.validateField('avatar', () => {})
      }
    }
  }
  
  // 重置表单
  const resetCreateForm = () => {
    createChatForm.value = {
      avatar: {
        objectName: '',
        objectUrl: '',
        objectThumbUrl: '',
      },
      chatName: '',
      joinEnable: 0,
      joinLinkExpiryMinutes: 10080,
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

  // 2. 自定义密码校验规则
  const validatePassword = (rule: any, value: string, callback: any) => {
    // 只在启用密码时进行校验
    if (createChatForm.value.joinEnable !== 1) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      callback()
      return
    }
    
    if (!value) {
      const errorMsg = t('validation.password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      callback(new Error(errorMsg))
    } else {
      // 密码输入后，清除错误状态
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      // 如果确认密码已输入，重新验证确认密码
      if (createChatForm.value.passwordConfirm !== '') {
        createFormRef.value?.validateField('passwordConfirm', () => {})
      }
      callback()
    }
  }

  const validatePasswordConfirm = (rule: any, value: string, callback: any) => {
    // 只在启用密码时进行校验
    if (createChatForm.value.joinEnable !== 1) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      callback()
      return
    }
    
    if (!value) {
      const errorMsg = t('validation.confirm_password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      callback(new Error(errorMsg))
    } else if (value !== createChatForm.value.password) {
      const errorMsg = t('validation.password_mismatch')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      callback(new Error(errorMsg))
    } else {
      // 验证通过，清除错误状态
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      callback()
    }
  }

  // 3. 校验规则定义
  const createFormRules = reactive<FormRules>({
    avatar: [
      {
        required: true,
        validator: (rule: any, value: any, callback: any) => {
          if (!value?.objectUrl) {
            callback(new Error(t('validation.avatar_required')))
          } else {
            callback()
          }
        },
        trigger: 'change',
      },
    ],
    chatName: [
      { required: true, message: t('validation.room_name_required'), trigger: 'blur' },
      { min: 1, max: 20, message: t('validation.room_name_length'), trigger: 'blur' },
    ],
    password: [{ validator: validatePassword as any, trigger: ['blur', 'change'] }],
    passwordConfirm: [{ validator: validatePasswordConfirm as any, trigger: ['blur', 'change'] }],
  })
  
  // 步骤验证
  const validateStep = async (step: number): Promise<boolean> => {
    if (!createFormRef.value) return false
    let fieldsToValidate: string[] = []
    if (step === 1) {
      // Step 1: 验证头像和房间名称
      fieldsToValidate = ['avatar', 'chatName']
    } else if (step === 2) {
      // Step 2: 如果开启了密码，验证密码字段
      if (createChatForm.value.joinEnable === 1) {
        fieldsToValidate = ['password', 'passwordConfirm']
      } else {
        // 未开启密码，清除错误状态并直接通过
        hasPasswordError.value = false
        passwordErrorMessage.value = ''
        return true
      }
    } else if (step === 3) {
      // Step 3: 过期时间无需验证
      return true
    }
    try {
      await createFormRef.value.validateField(fieldsToValidate)
      return true
    } catch (error) {
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
    if (!await validateStep(3)) return
    isCreating.value = true
    try {
      await createFormRef.value!.validate()
      // TODO: 调用创建聊天室 API
      // const result = await createChatApi(createChatForm.value)
      // if (result.status === 1) {
      //   createResult.value = { success: true, message: t('create_chat.success_msg') }
      //   createStep.value = 4
      // } else {
      //   createResult.value = { success: false, message: result.message || t('create_chat.fail_msg') }
      //   ElMessage.error(result.message || t('create_chat.fail_msg'))
      // }
      
      // 临时模拟成功
      createResult.value = { success: true, message: t('create_chat.success_msg') || '创建成功' }
      createStep.value = 4
    } catch (error: any) {
      const errorMessage = error.message || t('common.error')
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
  }
}
