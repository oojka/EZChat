import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, type UploadProps } from 'element-plus'
import type { Image } from '@/type'
import { useUserStore } from '@/stores/userStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts' // 引入 messageStore
import { Cooldown } from '@/utils/cooldown.ts'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import { calculateObjectHash } from '@/utils/objectHash'
import { checkObjectExistsApi } from '@/api/Media'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import i18n from '@/i18n'

const updateLock = new Cooldown(5000, 10, 10000)

export const useChatInput = () => {
  const { t } = i18n.global
  const userStore = useUserStore()

  const messageStore = useMessageStore() // 使用 messageStore

  // 定义待发送消息草稿结构（宽泛类型，避免 strict union 导致 type=0 时 images 必须为 never）
  const inputContent = ref<{
    sender: string
    chatCode: string
    type: number
    text: string
    images: Image[]
    createTime: string
    tempId: string
    status: 'sending' | 'sent' | 'error' | null
  }>({
    sender: '', // 发送时填充
    chatCode: '',
    type: 0,
    text: '',
    images: [], // 存储已上传成功的图片对象
    createTime: '',
    tempId: '',
    status: null, // 待发送消息初始状态为 null
  })

  // 新增：发送设置
  const sendSettings = ref({
    sendOnEnter: true, // 是否回车发送
  })

  // 上传前图片压缩的轻量状态（用于 UI 提示）
  const isImageProcessing = ref(false)

  // 1. 上传前校验：格式与大小 + 去重检查
  const beforePictureUpload: UploadProps['beforeUpload'] = async (rawFile) => {
    // 1.1 基础校验（格式、大小、频率限制）
    const isImage = isAllowedImageFile(rawFile as File)
    const isLtSize = rawFile.size / 1024 / 1024 < MAX_IMAGE_SIZE_MB

    if (!isImage) {
      ElMessage.error(t('validation.image_format'))
      return false
    }
    if (!isLtSize) {
      ElMessage.error(t('validation.image_size'))
      return false
    }
    if (!updateLock.canExecute) {
      const sec = updateLock.getRemainingTime()
      ElMessage.warning(t('auth.too_fast', { sec }))
      return false
    }

    try {
      isImageProcessing.value = true

      // 1.2 计算原始对象哈希（在压缩之前，确保是真正的原始对象）
      let rawHash: string
      try {
        rawHash = await calculateObjectHash(rawFile as File)
      } catch (e) {
        if (isAppError(e)) {
          throw createAppError(
            ErrorType.STATE,
            'Failed to calculate hash',
            {
              severity: ErrorSeverity.ERROR,
              component: 'useChatInput',
              action: 'beforePictureUpload',
              originalError: e
            })
        }
        // 哈希计算失败，降级为正常上传流程
        return await compressImage(rawFile as File)
      }

      // 1.3 调用比对接口，检查对象是否已存在
      try {
        const checkResult = await checkObjectExistsApi(rawHash)

        if (checkResult.status === 1 && checkResult.data) {
          // 对象已存在，直接使用返回的 Image 对象
          // 注意：这里需要手动触发成功回调，因为 el-upload 不会自动触发
          // 直接添加图片到输入内容，避免调用需要3个参数的 handlePictureSuccess
          inputContent.value.images.push(checkResult.data)

          // 返回 false 阻止 el-upload 实际上传对象
          return false
        }
      } catch (e) {
        if (isAppError(e)) {
          throw createAppError(
            ErrorType.STATE,
            'Failed to check object existence',
            {
              severity: ErrorSeverity.ERROR,
              component: 'useChatInput',
              action: 'beforePictureUpload',
              originalError: e
            })
        }
      }

      // 1.4 对象不存在或比对失败，继续正常上传流程
      // 压缩图片（失败则回退原图），并把 File 返回给 el-upload 替换上传内容
      return await compressImage(rawFile as File)
    } finally {
      isImageProcessing.value = false
    }
  }

  const handlePictureSuccess: UploadProps['onSuccess'] = (response) => {
    if (response) {
      inputContent.value.images.push(response.data)
    }
  }

  // 3. 删除预览图片
  const removeImage = async (index: number) => {
    inputContent.value.images.splice(index, 1)
  }

  // 4. 发送消息逻辑
  const send = () => {
    if (!inputContent.value.text.trim() && inputContent.value.images.length === 0) {
      return
    }

    // 使用 messageStore 发送消息
    // 注意：inputContent.value.images 是 Image[] 类型，符合接口要求
    messageStore.sendMessage(inputContent.value.text, inputContent.value.images)

    resetInput() // 发送后重置
  }

  // 新增：重置输入框逻辑
  const resetInput = () => {
    inputContent.value.text = ''
    inputContent.value.images = []
  }

  const uploadHeaders = computed<{
    token: string
  }>(() => ({
    token: userStore.getAccessToken()
  }))

  const handleExceed = () => {
    ElMessage.warning(t('validation.image_upload_limit'))
  }

  return {
    inputContent,
    sendSettings,
    uploadHeaders,
    beforePictureUpload,
    handlePictureSuccess,
    removeImage,
    send,
    resetInput, // 导出重置方法
    handleExceed,
    isImageProcessing,
  }
}
