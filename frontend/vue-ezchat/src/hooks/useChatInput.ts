import {computed, ref} from 'vue'
import {storeToRefs} from 'pinia'
import {ElMessage, type UploadProps} from 'element-plus'
import type {Message} from '@/type'
import {useUserStore} from '@/stores/userStore.ts'
import {useMessageStore} from '@/stores/messageStore.ts' // 引入 messageStore
import {Cooldown} from '@/utils/cooldown.ts'
import { compressImage } from '@/utils/imageCompressor'
import { isAllowedImageFile } from '@/utils/fileTypes'
import i18n from '@/i18n'

const updateLock = new Cooldown(5000, 10, 10000)

export const useChatInput = () => {
  const { t } = i18n.global
  const userStore = useUserStore()
  const { loginUser } = storeToRefs(userStore)

  const messageStore = useMessageStore() // 使用 messageStore

  // 定义待发送消息结构
  const inputContent = ref<Message>({
    sender: '', // 发送时填充
    chatCode: '',
    type: 0,
    text: '',
    images: [], // 存储已上传成功的图片对象
    createTime: '',
    tempId: '',
  })

  // 新增：发送设置
  const sendSettings = ref({
    sendOnEnter: true, // 是否回车发送
  })

  // 上传前图片压缩的轻量状态（用于 UI 提示）
  const isImageProcessing = ref(false)

  // 1. 上传前校验：格式与大小
  const beforePictureUpload: UploadProps['beforeUpload'] = async (rawFile) => {
    // 放宽图片类型限制：允许常见 image/*（并用扩展名兜底）
    const isImage = isAllowedImageFile(rawFile as File)
    const isLtSize = rawFile.size / 1024 / 1024 < 10 // 限制 10MB

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
    // 2) 压缩图片（失败则回退原图），并把 File 返回给 el-upload 替换上传内容
    try {
      isImageProcessing.value = true
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
    token: loginUser.value.token
  }))

  const handleExceed = () => {
    ElMessage.error('画像のアップロード件数が上限に達しました。')
  }

  return {
    loginUser,
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
