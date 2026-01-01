import {computed, ref} from 'vue'
import {type RegisterInfo, type Result} from '@/type'
import {ElMessage} from 'element-plus'
import {registerApi} from '@/api/Auth.ts'
import {useI18n} from 'vue-i18n'
import {getPasswordReg, USERNAME_REG} from '@/utils/validators.ts'
import { compressImage } from '@/utils/imageCompressor'

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
    if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
      ElMessage.error(t('validation.image_format')); return false
    } else if (rawFile.size / 1024 / 1024 > 10) {
      ElMessage.error(t('validation.image_size')); return false
    }
    // 前端压缩：失败则回退原图
    return await compressImage(rawFile)
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
