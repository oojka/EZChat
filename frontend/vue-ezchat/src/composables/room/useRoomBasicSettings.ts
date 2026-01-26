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

  /** 默认头像 URL（当未上传头像时显示） */
  const defaultAvatarUrl = ref('')
  /** 表单实例引用，用于执行校验 */
  const formRef = ref<FormInstance>()
  /** 保存状态，用于按钮 loading */
  const isSaving = ref(false)

  /** 房间设置表单数据模型 */
  const form = reactive({
    chatName: '',
    maxMembers: 200,
    announcement: '',
    avatar: { ...emptyAvatar },
  })

  /**
   * 构建头像图片对象
   *
   * 业务逻辑：
   * - 优先使用上传后的图片信息
   * - 如果某个字段为空，回退到 fallbackUrl
   *
   * @param avatar 当前头像对象
   * @param fallbackUrl 兜底图片 URL
   * @returns 完整的图片对象
   */
  const buildAvatarImage = (avatar: Image, fallbackUrl: string): Image => ({
    imageName: avatar.imageName || '',
    imageUrl: avatar.imageUrl || fallbackUrl,
    imageThumbUrl: avatar.imageThumbUrl || fallbackUrl,
    blobUrl: avatar.blobUrl || '',
    blobThumbUrl: avatar.blobThumbUrl || '',
    assetId: avatar.assetId,
  })

  /** 当前用于展示的头像（计算属性） */
  const displayAvatar = computed(() => {
    const fallback = defaultAvatarUrl.value || ''
    return buildAvatarImage(form.avatar, fallback)
  })

  /**
   * 从 Store 同步当前房间信息到表单
   *
   * 每次打开弹窗或房间信息更新时调用，确保表单显示最新数据
   */
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

  /**
   * 头像上传成功回调
   *
   * @param response 后端返回的上传结果
   */
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

  /**
   * 头像上传前置钩子
   *
   * 业务逻辑：
   * 1. 校验文件格式（仅限图片）和大小（<10MB）
   * 2. 计算文件哈希（SHA-256）
   * 3. 检查后端是否存在相同哈希的文件（秒传机制）
   *    - 若存在：直接复用，跳过物理上传
   *    - 若不存在：压缩图片后继续上传流程
   *
   * @param rawFile 原始文件对象
   * @returns false=停止上传（或已秒传成功），Promise<File>=压缩后的文件
   */
  const beforeAvatarUpload: UploadProps['beforeUpload'] = async (rawFile) => {
    // 1. 基础校验：是否为文件对象
    if (!isFile(rawFile)) {
      ElMessage.error(t('validation.image_format') || 'Invalid image format')
      return false
    }
    // 2. 格式校验：是否为允许的图片类型
    if (!isAllowedImageFile(rawFile)) {
      ElMessage.error(t('validation.image_format') || 'Invalid image format')
      return false
    }
    // 3. 大小校验
    if (rawFile.size / 1024 / 1024 >= MAX_IMAGE_SIZE_MB) {
      ElMessage.error(t('validation.image_size') || 'Image too large')
      return false
    }

    try {
      // 4. 计算哈希，尝试秒传
      const rawHash = await calculateObjectHash(rawFile)
      try {
        const checkResult = await checkObjectExistsApi(rawHash)
        // 如果后端已存在该文件 (status === 1)
        if (checkResult.status === 1 && checkResult.data) {
          const imageData: Image = {
            ...checkResult.data,
            blobUrl: checkResult.data.blobUrl || '',
            blobThumbUrl: checkResult.data.blobThumbUrl || ''
          }
          // 模拟上传成功回调
          const response: UploadSuccessResponse = { data: imageData }
          const emptyUploadFile: Partial<UploadFile> = {}
          handleAvatarSuccess(response, emptyUploadFile as UploadFile, [])
          // 拦截实际上传请求
          return false
        }
      } catch (error) {
        console.error('[ERROR] [RoomBasic] Failed to check object existence:', error)
      }

      // 5. 秒传失败或文件不存在，进行压缩并上传
      return await compressImage(rawFile)
    } catch (error) {
      console.error('[ERROR] [RoomBasic] Failed to calculate hash:', error)
      // 降级处理：直接压缩上传
      return await compressImage(rawFile)
    }
  }

  /**
   * 保存基础设置
   *
   * 业务逻辑：
   * 1. 校验表单数据
   * 2. 调用更新 API
   * 3. 成功后更新 Store 中的房间信息
   */
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
