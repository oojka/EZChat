import {computed, ref} from 'vue'
import {type RegisterInfo, type Result} from '@/type'
import {ElMessage} from 'element-plus'
import {registerApi} from '@/api/Auth.ts'
import {useI18n} from 'vue-i18n'
import {getPasswordReg, USERNAME_REG} from '@/utils/validators.ts'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import { calculateObjectHash } from '@/utils/objectHash'
import { checkObjectExistsApi } from '@/api/Media'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'

export function useRegister() {
  const { t } = useI18n()
  const registerFormRef = ref()

  // 仅保留无法通过正则实现的“两次密码一致”校验
  const validateConfirmPassword = (rule: any, value: any, callback: any) => {
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
      { pattern: USERNAME_REG, message: t('validation.username_format'), trigger: 'blur' }
    ],
    password: [
      { required: true, message: t('validation.password_required'), trigger: 'blur' },
      { pattern: getPasswordReg({ min: 8, max: 20, level: 'basic' }), message: t('validation.password_format'), trigger: 'blur' }
    ],
    confirmPassword: [
      { required: true, validator: validateConfirmPassword, trigger: 'blur' },
    ],
    avatar: [
      {
        required: true,
        validator: (rule: any, value: any, callback: any) => {
          if (!value?.objectUrl) callback(new Error(t('validation.avatar_required')))
          else callback()
        },
        trigger: 'change',
      },
    ],
  }))

  const registerForm = ref<RegisterInfo>({
    nickname: '', username: '', password: '', confirmPassword: '',
    avatar: { objectName: '', objectUrl: '', objectThumbUrl: '' },
  })

  const beforeAvatarUpload = async (rawFile: File) => {
    // 放宽图片类型限制：允许常见 image/*（并用扩展名兜底）
    if (!isAllowedImageFile(rawFile)) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    const fileSizeMB = rawFile.size / 1024 / 1024
    if (fileSizeMB >= MAX_IMAGE_SIZE_MB) {
      ElMessage.error(t('validation.image_size'))
      return false
    }

    try {
      // 计算原始对象哈希（在压缩之前，确保是真正的原始对象）
      const rawHash = await calculateObjectHash(rawFile)
      
      // 调用比对接口，检查对象是否已存在
      try {
        const checkResult = await checkObjectExistsApi(rawHash)
        
        if (checkResult.status === 1 && checkResult.data) {
          // 对象已存在，直接使用返回的 Image 对象
          handleAvatarSuccess(checkResult)
          // 返回 false 阻止 el-upload 实际上传对象
          return false
        }
      } catch (error) {
        console.error('[ERROR] [beforeAvatarUpload] Failed to check object existence:', error)
        // 比对接口失败，降级为正常上传流程（继续上传）
      }

      // 对象不存在或比对失败，继续正常上传流程
      // 前端压缩：失败则回退原图
      return await compressImage(rawFile)
    } catch (error) {
      console.error('[ERROR] [beforeAvatarUpload] Failed to calculate hash:', error)
      // 哈希计算失败，降级为正常上传流程
      return await compressImage(rawFile)
    }
  }

  const handleAvatarSuccess = (response: Result) => {
    registerForm.value.avatar = response.data
    if (registerFormRef.value) registerFormRef.value.validateField('avatar')
  }

  const resetRegisterForm = () => {
    registerForm.value = {
      nickname: '', username: '', password: '', confirmPassword: '',
      avatar: { objectName: '', objectUrl: '', objectThumbUrl: '' },
    }
    if (registerFormRef.value) registerFormRef.value.resetFields()
  }

  const register = async (): Promise<boolean> => {
    if (!registerFormRef.value) return false
    try {
      const valid = await registerFormRef.value.validate()
      if (!valid) return false
      
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
