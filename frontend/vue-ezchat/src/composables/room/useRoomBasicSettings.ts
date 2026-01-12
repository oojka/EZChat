/**
 * 房间基础设置 Composable
 *
 * 核心职责：
 * - 管理房间基本信息表单（名称、人数上限、公告、头像）
 * - 处理头像上传（含去重检查和压缩）
 * - 同步表单数据与当前房间信息
 * - 执行保存 API 并更新 Store
 *
 * 使用示例：
 * ```vue
 * const { form, formRules, saveBasicSettings } = useRoomBasicSettings()
 * ```
 *
 * @module useRoomBasicSettings
 */
import { computed, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, type FormInstance, type FormRules, type UploadProps, type UploadFile } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { useImageStore } from '@/stores/imageStore'
import { updateChatBasicInfoApi } from '@/api/Chat'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import { calculateObjectHash } from '@/utils/objectHash'
import { checkObjectExistsApi } from '@/api/Media'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'
import { isImage } from '@/utils/validators'
import type { Image } from '@/type'

/** 空头像对象模板 */
const emptyAvatar: Image = {
  imageName: '',
  imageUrl: '',
  imageThumbUrl: '',
  blobUrl: '',
  blobThumbUrl: '',
}

type UploadSuccessResponse = {
  data: Image
}

const isFile = (value: unknown): value is File => value instanceof File

export const useRoomBasicSettings = () => {
  const { t } = useI18n()
  const roomStore = useRoomStore()
  const imageStore = useImageStore()
  const { currentRoom, roomSettingsDialogVisible } = storeToRefs(roomStore)

  const defaultAvatarUrl = ref('')
  const formRef = ref<FormInstance>()
  const isSaving = ref(false)

  const form = reactive({
    chatName: '',
    maxMembers: 200,
    announcement: '',
    avatar: { ...emptyAvatar },
  })

  const buildAvatarImage = (avatar: Image, fallbackUrl: string): Image => ({
    imageName: avatar.imageName || '',
    imageUrl: avatar.imageUrl || fallbackUrl,
    imageThumbUrl: avatar.imageThumbUrl || fallbackUrl,
    blobUrl: avatar.blobUrl || '',
    blobThumbUrl: avatar.blobThumbUrl || '',
    assetId: avatar.assetId,
  })

  const displayAvatar = computed(() => {
    const fallback = defaultAvatarUrl.value || ''
    return buildAvatarImage(form.avatar, fallback)
  })

  const syncFromRoom = () => {
    if (!currentRoom.value) return
    form.chatName = currentRoom.value.chatName || ''
    form.maxMembers = currentRoom.value.maxMembers || 200
    form.announcement = currentRoom.value.announcement || ''
    form.avatar = currentRoom.value.avatar ? { ...currentRoom.value.avatar } : { ...emptyAvatar }
  }

  watch(roomSettingsDialogVisible, (visible) => {
    if (visible) {
      defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('room')
      syncFromRoom()
    }
  }, { immediate: true })

  watch(currentRoom, () => {
    if (roomSettingsDialogVisible.value) {
      syncFromRoom()
    }
  })

  const formRules: FormRules = {
    chatName: [
      { required: true, message: t('validation.room_name_required') || 'Chat name is required', trigger: 'blur' },
      { min: 1, max: 20, message: t('validation.room_name_length') || 'Max 20 characters', trigger: 'blur' }
    ],
    maxMembers: [
      { required: true, message: t('validation.chat_max_members_required') || 'Max members is required', trigger: 'change' },
      {
        validator: (_rule, value, callback) => {
          if (typeof value !== 'number' || value < 2 || value > 200) {
            callback(new Error(t('validation.chat_max_members_range') || 'Range 2-200'))
            return
          }
          callback()
        },
        trigger: 'change'
      }
    ],
    announcement: [
      {
        validator: (_rule, value, callback) => {
          if (typeof value === 'string' && value.length > 500) {
            callback(new Error(t('validation.chat_announcement_length') || 'Max 500 characters'))
            return
          }
          callback()
        },
        trigger: 'blur'
      }
    ]
  }

  const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
    if (response && typeof response === 'object' && 'data' in response) {
      const data = (response as UploadSuccessResponse).data
      if (isImage(data)) {
        form.avatar = { ...data }
        return
      }
    }
    ElMessage.error(t('room_settings.basic_avatar_upload_failed') || 'Avatar upload failed')
  }

  const beforeAvatarUpload: UploadProps['beforeUpload'] = async (rawFile) => {
    if (!isFile(rawFile)) {
      ElMessage.error(t('validation.image_format') || 'Invalid image format')
      return false
    }
    if (!isAllowedImageFile(rawFile)) {
      ElMessage.error(t('validation.image_format') || 'Invalid image format')
      return false
    }
    if (rawFile.size / 1024 / 1024 >= MAX_IMAGE_SIZE_MB) {
      ElMessage.error(t('validation.image_size') || 'Image too large')
      return false
    }

    try {
      const rawHash = await calculateObjectHash(rawFile)
      try {
        const checkResult = await checkObjectExistsApi(rawHash)
        if (checkResult.status === 1 && checkResult.data) {
          const imageData: Image = {
            ...checkResult.data,
            blobUrl: checkResult.data.blobUrl || '',
            blobThumbUrl: checkResult.data.blobThumbUrl || ''
          }
          const response: UploadSuccessResponse = { data: imageData }
          const emptyUploadFile: Partial<UploadFile> = {}
          handleAvatarSuccess(response, emptyUploadFile as UploadFile, [])
          return false
        }
      } catch (error) {
        console.error('[ERROR] [RoomBasic] Failed to check object existence:', error)
      }

      return await compressImage(rawFile)
    } catch (error) {
      console.error('[ERROR] [RoomBasic] Failed to calculate hash:', error)
      return await compressImage(rawFile)
    }
  }

  const saveBasicSettings = async () => {
    if (!formRef.value || !currentRoom.value) return
    const room = currentRoom.value

    await formRef.value.validate(async (valid) => {
      if (!valid) return
      isSaving.value = true
      try {
        const payload = {
          chatName: form.chatName,
          maxMembers: form.maxMembers,
          announcement: form.announcement,
          avatar: form.avatar,
        }
        const res = await updateChatBasicInfoApi(room.chatCode, payload)
        if (res && res.status === 1 && res.data) {
          roomStore.updateRoomInfo(res.data)
          ElMessage.success(t('room_settings.basic_update_success') || 'Updated')
          return
        }
        ElMessage.error(res?.message || t('room_settings.basic_update_failed') || 'Update failed')
      } catch (error) {
        console.error('[ERROR] [RoomBasic] Update failed:', error)
        ElMessage.error(t('room_settings.basic_update_failed') || 'Update failed')
      } finally {
        isSaving.value = false
      }
    })
  }

  return {
    form,
    formRef,
    formRules,
    isSaving,
    displayAvatar,
    beforeAvatarUpload,
    handleAvatarSuccess,
    saveBasicSettings,
  }
}
