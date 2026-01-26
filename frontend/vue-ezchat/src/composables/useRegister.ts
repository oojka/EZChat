/**
 * 用户注册 Composable
 *
 * 核心职责：
 * - 管理注册表单状态和验证规则
 * - 处理头像上传（仅压缩，不检查去重）
 * - 执行用户注册 API 调用
 * - 提供表单重置功能
 *
 * 注意：注册时用户未登录，无法调用需要认证的 /media/check 接口，
 * 因此跳过前端去重检查，直接压缩后上传。后端会处理去重。
 *
 * 使用示例：
 * ```vue
 * const { registerForm, register, registerFormRules } = useRegister()
 * ```
 *
 * @module useRegister
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { registerApi } from '@/api/Auth.ts'
import { useI18n } from 'vue-i18n'
import { getPasswordReg, REGEX_USERNAME } from '@/utils/validators.ts'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'
import { useImageStore } from '@/stores/imageStore'

import type { RegisterInfo, Result, Image} from '@/type'
import type { InternalRuleItem } from 'async-validator'

/**
 * 用户注册业务逻辑 Hook
 *
 * @returns 注册表单、验证规则、头像处理方法等
 */
export function useRegister() {
  const { t } = useI18n()
  const registerFormRef = ref()
  const imageStore = useImageStore()

  // 仅保留无法通过正则实现的"两次密码一致"校验
  const validateConfirmPassword = (_rule: InternalRuleItem, value: unknown, callback: (error?: Error) => void) => {
    if (!value) callback(new Error(t('validation.confirm_password_required')))
    else if (value !== registerForm.value.password) callback(new Error(t('validation.password_mismatch')))
    else callback()
  }

  // 表单校验规则：直接使用 pattern，去掉函数包装
  const registerFormRules = computed(() => ({
    nickname: [
      { required: true, message: t('validation.nickname_required'), trigger: 'blur' },
      { min: 2, max: 20, message: t('validation.nickname_length'), trigger: 'blur' },
    ],
    username: [
      { required: true, message: t('validation.username_required'), trigger: 'blur' },
      { pattern: REGEX_USERNAME, message: t('validation.username_format'), trigger: 'blur' }
    ],
    password: [
      { required: true, message: t('validation.password_required'), trigger: 'blur' },
      { pattern: getPasswordReg({ min: 8, max: 20, level: 'basic' }), message: t('validation.password_format'), trigger: 'blur' }
    ],
    confirmPassword: [
      { required: true, validator: validateConfirmPassword, trigger: 'blur' },
    ],
  }))

  const registerForm = ref<RegisterInfo>({
    nickname: '', username: '', password: '', confirmPassword: '',
    avatar: { imageName: '', imageUrl: '', imageThumbUrl: '' },
  })

  const beforeAvatarUpload = async (rawFile: File) => {
    if (!isAllowedImageFile(rawFile)) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    const fileSizeMB = rawFile.size / 1024 / 1024
    if (fileSizeMB >= MAX_IMAGE_SIZE_MB) {
      ElMessage.error(t('validation.image_size'))
      return false
    }

    // 注册时用户未登录，跳过 /media/check 去重检查，直接压缩上传
    // 后端 uploadAvatar 会处理去重（通过 normalized hash）
    return await compressImage(rawFile)
  }

  const handleAvatarSuccess = (response: Result<Image | null>) => {
    if (response.data) {
      registerForm.value.avatar = response.data
      if (registerFormRef.value) registerFormRef.value.validateField('avatar')
    }
  }

  const resetRegisterForm = () => {
    registerForm.value = {
      nickname: '', username: '', password: '', confirmPassword: '',
      avatar: { imageName: '', imageUrl: '', imageThumbUrl: '' },
    }
    if (registerFormRef.value) registerFormRef.value.resetFields()
  }

  const register = async (): Promise<boolean> => {
    if (!registerFormRef.value) return false
    try {
      // 如果用户未上传头像，上传默认头像
      if (!registerForm.value.avatar.imageUrl && !registerForm.value.avatar.imageThumbUrl) {
        registerForm.value.avatar = await imageStore.uploadDefaultAvatarIfNeeded(registerForm.value.avatar)
      }

      const result = await registerApi(registerForm.value)
      // 检查返回结果的 status，只有 status === 1 才表示成功
      return result.status === 1
    } catch (error) {
      console.error('[ERROR] [Register] Validation or API Error:', error)
      throw error // 重新抛出错误，让调用方处理
    }
  }

  return {
    registerForm, register, registerFormRules, registerFormRef,
    beforeAvatarUpload, handleAvatarSuccess, resetRegisterForm,
  }
}
