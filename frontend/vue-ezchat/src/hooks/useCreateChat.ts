import { reactive, ref, watch } from 'vue'
import type {Image} from '@/type'
import {ElMessage, type FormInstance, type FormRules, type UploadProps, type FormItemRule} from 'element-plus'
import { useI18n } from 'vue-i18n'

export const useCreateChat = () => {
  const { t } = useI18n()
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

  const disabledDate = (time: Date) => {
    const now = new Date()
    const startOfToday = new Date(now.setHours(0, 0, 0, 0))
    const thirtyDaysLater = new Date(startOfToday.getTime() + 31 * 24 * 60 * 60 * 1000)
    return time.getTime() < startOfToday.getTime() || time.getTime() > thirtyDaysLater.getTime()
  }

  const createFormRef = ref<FormInstance>()

  // 1. 头像上传逻辑
  const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
    const isImage = rawFile.type === 'image/jpeg' || rawFile.type === 'image/png'
    const isLt2M = rawFile.size / 1024 / 1024 < 2

    if (!isImage) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    if (!isLt2M) {
      ElMessage.error(t('validation.image_size'))
      return false
    }
    return true
  }

  const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    if (response && response.data) {
      createChatForm.value.avatar = response.data
    }
  }

  // 2. 自定义密码校验规则
  const validatePassword = (rule: FormItemRule, value: string, callback: (error?: Error) => void) => {
    if (createChatForm.value.joinEnable === 1) {
      if (!value) {
        callback(new Error(t('validation.password_required')))
      } else {
        if (createChatForm.value.passwordConfirm !== '') {
          createFormRef.value?.validateField('passwordConfirm', () => {})
        }
        callback()
      }
    } else {
      callback()
    }
  }

  const validatePasswordConfirm = (rule: FormItemRule, value: string, callback: (error?: Error) => void) => {
    if (createChatForm.value.joinEnable === 1) {
      if (!value) {
        callback(new Error(t('validation.confirm_password_required')))
      } else if (value !== createChatForm.value.password) {
        callback(new Error(t('validation.password_mismatch')))
      } else {
        callback()
      }
    } else {
      callback()
    }
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

  // 4. 提交包装函数
  const handleCreate = async () => {
    if (!createFormRef.value) return
    try {
      await createFormRef.value.validate()
      console.log('[INFO] [CreateChatHook] Create logic:', createChatForm.value)
    } catch (error) {
      console.log('[DEBUG] [CreateChatHook] Validation failed:', error)
    }
  }

  return {
    createChatForm,
    handleCreate,
    createFormRef,
    createFormRules,
    beforeAvatarUpload,
    handleAvatarSuccess,
    selectedDate,
    selectedDateRadio,
    disabledDate,
  }
}
