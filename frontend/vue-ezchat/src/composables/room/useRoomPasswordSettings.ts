/**
 * 房间密码设置 Composable
 *
 * 核心职责：
 * - 管理密码保护开关状态
 * - 处理密码设置/修改表单及验证
 * - 执行密码更新 API 并同步 Store
 * - 提供编辑模式切换逻辑
 *
 * 使用示例：
 * ```vue
 * const { passwordForm, isEditing, savePasswordSettings } = useRoomPasswordSettings()
 * ```
 *
 * @module useRoomPasswordSettings
 */
import { reactive, ref, watch } from 'vue'
import type { FormRules, FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { updateChatPasswordApi } from '@/api/Chat'

/**
 * 房间密码设置业务逻辑 Hook
 *
 * @returns 密码表单、编辑状态、保存方法等
 */
export const useRoomPasswordSettings = () => {
  const { t } = useI18n()
  const roomStore = useRoomStore()
  const { currentRoom, roomSettingsDialogVisible } = storeToRefs(roomStore)

  const isSaving = ref(false)
  const isEditing = ref(false)
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

  const clearPasswordFields = () => {
    passwordForm.value.password = ''
    passwordForm.value.passwordConfirm = ''
  }

  const resetForm = () => {
    passwordForm.value.joinEnableByPassword = 0
    clearPasswordFields()
    hasPasswordError.value = false
    passwordErrorMessage.value = ''
    isEditing.value = false
  }

  const syncFromRoom = () => {
    const joinEnabled = currentRoom.value?.passwordEnabled
    if (joinEnabled === 0 || joinEnabled === 1) {
      passwordForm.value.joinEnableByPassword = joinEnabled
    }
  }

  watch(roomSettingsDialogVisible, (visible) => {
    if (!visible) {
      resetForm()
      return
    }
    syncFromRoom()
    clearPasswordFields()
    passwordFormRef.value?.clearValidate()
    isEditing.value = false
  }, { immediate: true })

  watch(currentRoom, () => {
    if (!roomSettingsDialogVisible.value) return
    syncFromRoom()
    clearPasswordFields()
    passwordFormRef.value?.clearValidate()
    isEditing.value = false
  })

  watch(() => passwordForm.value.joinEnableByPassword, (val) => {
    if (val === 0) {
      clearPasswordFields()
      hasPasswordError.value = false
      passwordErrorMessage.value = ''
      passwordFormRef.value?.resetFields()
      isEditing.value = false
    }
  })

  const handleToggle = async (value: 0 | 1) => {
    if (value === 1) {
      return
    }
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return
    isSaving.value = true
    try {
      await updateChatPasswordApi(chatCode, {
        joinEnableByPassword: 0,
        password: '',
        passwordConfirm: '',
      })
      ElMessage.success(t('room_settings.password_update_success'))
      if (currentRoom.value) {
        roomStore.updateRoomInfo({
          ...currentRoom.value,
          passwordEnabled: 0,
        })
      }
    } catch (error) {
      console.error('[ERROR] [RoomPassword] Failed to disable password:', error)
      ElMessage.error(t('room_settings.password_update_failed'))
      passwordForm.value.joinEnableByPassword = currentRoom.value?.passwordEnabled === 1 ? 1 : 0
    } finally {
      isSaving.value = false
    }
  }

  const openEditor = () => {
    if (passwordForm.value.joinEnableByPassword === 0) return
    isEditing.value = true
    clearPasswordFields()
    passwordFormRef.value?.clearValidate()
  }

  const cancelEdit = () => {
    isEditing.value = false
    clearPasswordFields()
    passwordFormRef.value?.clearValidate()
  }

  const savePasswordSettings = async () => {
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return
    if (!isEditing.value) return

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
      if (currentRoom.value) {
        roomStore.updateRoomInfo({
          ...currentRoom.value,
          passwordEnabled: 1,
        })
      }
      isEditing.value = false
      clearPasswordFields()
      passwordFormRef.value?.clearValidate()
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
    isEditing,
    hasPasswordError,
    passwordErrorMessage,
    handleToggle,
    openEditor,
    cancelEdit,
    savePasswordSettings,
    resetForm,
  }
}
