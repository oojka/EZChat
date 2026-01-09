import { reactive, ref, watch } from 'vue'
import type { FormRules, FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { updateChatPasswordApi } from '@/api/Chat'

export const useRoomPasswordSettings = () => {
  const { t } = useI18n()
  const roomStore = useRoomStore()
  const { currentRoom, roomSettingsDialogVisible } = storeToRefs(roomStore)

  const isSaving = ref(false)
  const hasPasswordError = ref(false)
  const passwordErrorMessage = ref('')

  const passwordForm = ref({
    joinEnableByPassword: 0 as 0 | 1,
    password: '',
    passwordConfirm: '',
  })

  const isStringValue = (value: unknown): value is string => typeof value === 'string'

  const validatePassword = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (!isStringValue(value)) {
      return callback(new Error(t('validation.password_required')))
    }
    if (passwordForm.value.joinEnableByPassword === 0) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      return callback()
    }
    if (!value) {
      const errorMsg = t('validation.password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    if (passwordForm.value.passwordConfirm) {
      passwordFormRef.value?.validateField('passwordConfirm', () => {})
    }
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    return callback()
  }

  const validatePasswordConfirm = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (!isStringValue(value)) {
      return callback(new Error(t('validation.confirm_password_required')))
    }
    if (passwordForm.value.joinEnableByPassword === 0) {
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      return callback()
    }
    if (!value) {
      const errorMsg = t('validation.confirm_password_required')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    if (value !== passwordForm.value.password) {
      const errorMsg = t('validation.password_mismatch')
      hasPasswordError.value = true
      passwordErrorMessage.value = errorMsg
      return callback(new Error(errorMsg))
    }
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    return callback()
  }

  const passwordFormRules = reactive<FormRules>({
    password: [{ validator: validatePassword, trigger: ['blur', 'change'] }],
    passwordConfirm: [{ validator: validatePasswordConfirm, trigger: ['blur', 'change'] }],
  })

  const passwordFormRef = ref<FormInstance | null>(null)

  const resetForm = () => {
    passwordForm.value.joinEnableByPassword = 0
    passwordForm.value.password = ''
    passwordForm.value.passwordConfirm = ''
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
  }

  const clearPasswordFields = () => {
    passwordForm.value.password = ''
    passwordForm.value.passwordConfirm = ''
  }

  watch(roomSettingsDialogVisible, (visible) => {
    if (!visible) {
      resetForm()
    }
  }, { immediate: true })

  watch(() => passwordForm.value.joinEnableByPassword, (val) => {
    if (val === 0) {
      passwordForm.value.password = ''
      passwordForm.value.passwordConfirm = ''
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      passwordFormRef.value?.resetFields()
    }
  })

  const savePasswordSettings = async () => {
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return

    if (passwordForm.value.joinEnableByPassword === 1 && passwordFormRef.value) {
      try {
        await passwordFormRef.value.validate()
      } catch {
        return
      }
    }

    isSaving.value = true
    try {
      await updateChatPasswordApi(chatCode, {
        joinEnableByPassword: passwordForm.value.joinEnableByPassword,
        password: passwordForm.value.joinEnableByPassword === 1 ? passwordForm.value.password : '',
        passwordConfirm: passwordForm.value.joinEnableByPassword === 1 ? passwordForm.value.passwordConfirm : '',
      })
      ElMessage.success(t('room_settings.password_update_success'))
      clearPasswordFields()
    } catch (error) {
      console.error('[ERROR] [RoomPassword] Failed to update password:', error)
      ElMessage.error(t('room_settings.password_update_failed'))
    } finally {
      isSaving.value = false
    }
  }

  return {
    passwordForm,
    passwordFormRef,
    passwordFormRules,
    isSaving,
    hasPasswordError,
    passwordErrorMessage,
    savePasswordSettings,
    resetForm,
  }
}
