import {computed, ref} from 'vue'
import {storeToRefs} from 'pinia'
import {ElMessage, type UploadProps} from 'element-plus'
import type {Message} from '@/type'
import {useUserStore} from '@/stores/userStore.ts'
import {useMessageStore} from '@/stores/messageStore.ts' // 引入 messageStore
import {Cooldown} from '@/utils/cooldown.ts'

const updateLock = new Cooldown(5000, 10, 10000)

export const useChatInput = () => {
  const userStore = useUserStore()
  const { loginUser } = storeToRefs(userStore)

  const messageStore = useMessageStore() // 使用 messageStore

  // 定义待发送消息结构
  const inputContent = ref<Message>({
    sender: '', // 发送时填充
    chatCode: '',
    text: '',
    images: [], // 存储已上传成功的图片对象
    createTime: '',
    tempId: '',
  })

  // 新增：发送设置
  const sendSettings = ref({
    sendOnEnter: true, // 是否回车发送
  })

  // 1. 上传前校验：格式与大小
  const beforePictureUpload: UploadProps['beforeUpload'] = (rawFile) => {
    const isImage =
      rawFile.type === 'image/jpeg' || rawFile.type === 'image/png' || rawFile.type === 'image/gif'
    const isLtSize = rawFile.size / 1024 / 1024 < 10 // 限制 10MB

    if (!isImage) {
      ElMessage.error('画像はJPG/PNG/GIF形式のみ可能です')
      return false
    }
    if (!isLtSize) {
      ElMessage.error('画像サイズは10MB以下にしてください')
      return false
    }
    if (!updateLock.canExecute) {
      const sec = updateLock.getRemainingTime()
      ElMessage.warning(`操作が頻繁すぎます。あと ${sec} 秒待ってください。`)
      return false
    }
    return true
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
    handleExceed
  }
}
