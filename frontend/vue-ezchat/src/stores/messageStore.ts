import {defineStore, storeToRefs} from 'pinia'
import type {ChatRoom, Image, Message} from '@/type'
import {ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {getMessageListApi} from '@/api/Message.ts'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useAppStore} from '@/stores/appStore.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {useWebsocketStore} from '@/stores/websocketStore.ts'
import {showMessageNotification} from '@/utils/notification.ts'
import axios from 'axios'
import i18n from '@/i18n' // 引入 i18n

// 兼容性更好的 ID 生成器
function generateTempId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2)
}

export const useMessageStore = defineStore('message', () => {
  const router = useRouter()
  const { t } = i18n.global // 获取翻译函数

  // --- 状态定义 ---
  const currentMessageList = ref<Message[]>([])
  const chatViewIsLoading = ref<boolean>(false)
  const loadingMessages = ref(false)
  const noMoreMessages = ref(false)
  const isLoading = ref<boolean>(false)

  // --- Blob 处理逻辑 ---
  const fetchBlob = async (url: string): Promise<string | undefined> => {
    if (!url) return undefined
    try {
      const response = await axios.get(url, { responseType: 'blob' })
      return URL.createObjectURL(response.data)
    } catch (e) {
      console.error('[Blob] Failed to load image:', url, e)
      return undefined
    }
  }

  const processMessageImages = async (messages: Message[]) => {
    for (const msg of messages) {
      if (!msg.images || msg.images.length === 0) continue
      for (const img of msg.images) {
        if (img.blobUrl || img.blobThumbUrl) continue
        if (img.objectUrl) {
          fetchBlob(img.objectUrl).then(url => { if (url) img.blobUrl = url })
        }
        if (img.objectThumbUrl) {
          fetchBlob(img.objectThumbUrl).then(url => { if (url) img.blobThumbUrl = url })
        }
      }
    }
  }

  const revokeAllBlobs = () => {
    currentMessageList.value.forEach(msg => {
      msg.images?.forEach(img => {
        if (img.blobUrl) URL.revokeObjectURL(img.blobUrl)
        if (img.blobThumbUrl) URL.revokeObjectURL(img.blobThumbUrl)
        img.blobUrl = undefined
        img.blobThumbUrl = undefined
      })
    })
  }

  // --- 业务逻辑 ---
  const getMessageList = async (createTime?: string) => {
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    if (!currentRoomCode.value) return

    try {
      if (!createTime) loadingMessages.value = true
      const result = await getMessageListApi(currentRoomCode.value, createTime || '')
      if (result) {
        const newMessages = result.data.messageList
        const newChatRoom: ChatRoom = result.data.chatRoom
        if (newChatRoom) roomStore.updateRoomInfo(newChatRoom)
        if (newMessages.length === 0) {
          if (createTime) noMoreMessages.value = true
          return
        }
        const existingKeys = new Set(currentMessageList.value.map((m) => `${m.sender}_${m.createTime}`))
        const uniqueMessages = newMessages.filter((m) => {
          const key = `${m.sender}_${m.createTime}`
          return !m.createTime || !existingKeys.has(key)
        })
        if (uniqueMessages.length === 0) {
          if (createTime) noMoreMessages.value = true
          return
        }
        await processMessageImages(uniqueMessages)
        if (!createTime) currentMessageList.value = uniqueMessages
        else currentMessageList.value = [...currentMessageList.value, ...uniqueMessages]
      }
    } catch (e) {
      await Promise.reject(new Error('Failed to load messages'))
    } finally {
      loadingMessages.value = false
    }
  }

  const loadMoreHistory = async () => {
    if (loadingMessages.value || noMoreMessages.value) return
    if (currentMessageList.value.length === 0) return
    loadingMessages.value = true
    const oldestMessage = currentMessageList.value[currentMessageList.value.length - 1]
    await getMessageList(oldestMessage?.createTime)
    loadingMessages.value = false
  }

  const sendMessage = (text: string, images: Image[] = []) => {
    const roomStore = useRoomStore()
    const userStore = useUserStore()
    const websocketStore = useWebsocketStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    const { loginUser } = storeToRefs(userStore)
    if (!currentRoomCode.value || !loginUser.value.uid) return

    const imagesCopy = [...images]
    processMessageImages([{images: imagesCopy} as Message]).then(r => {})
     const tempId = generateTempId()
    const newMessage: Message = {
      sender: loginUser.value.uid,
      chatCode: currentRoomCode.value,
      text: text,
      images: imagesCopy,
      createTime: new Date().toISOString(),
      tempId: tempId,
      status: 'sending'
    }
    currentMessageList.value.unshift(newMessage)
    const payload = { chatCode: currentRoomCode.value, text: text, images: imagesCopy, tempId: tempId, sender: loginUser.value.uid }
    websocketStore.sendData(payload)
    setTimeout(() => {
      const msg = currentMessageList.value.find(m => m.tempId === tempId)
      if (msg && msg.status === 'sending') msg.status = 'error'
    }, 10000)
  }

  const handleAck = (tempId: string) => {
    const msg = currentMessageList.value.find(m => m.tempId === tempId)
    if (msg) msg.status = 'sent'
  }

  const receiveMessage = async (message: Message) => {
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    await processMessageImages([message])
    if (message.chatCode === currentRoomCode.value) {
      const userStore = useUserStore()
      if (message.sender === userStore.loginUser.uid) return
      currentMessageList.value.unshift(message)
      roomStore.updateRoomPreview(message)
      return
    }
    const chat = roomStore.getRoomByCode(message.chatCode)
    const sender = chat?.chatMembers.find((m) => m.uid === message.sender)
    if (chat && sender) {
      message.text = formatPreviewMessage(message)
      showMessageNotification(message, sender, chat.chatName)
      roomStore.updateRoomPreview(message)
    } else {
      await roomStore.initRoomList()
    }
  }

  /**
   * 格式化预览消息 (国际化适配)
   */
  const formatPreviewMessage = (message: Message): string => {
    let result = message.text || ''
    if (message.images && message.images.length > 0) {
      // 使用翻译后的 [画像] 标签
      result += `[${t('chat.image')}]`.repeat(message.images.length)
    }
    // 使用翻译后的 [新消息] 占位符
    return result || `[${t('chat.new_message')}]`
  }

  watch(
    () => router.currentRoute.value.params.chatCode,
    async (newCode) => {
      const roomStore = useRoomStore()
      const appStore = useAppStore()
      const { currentRoomCode } = storeToRefs(roomStore)
      const { isAppLoading } = storeToRefs(appStore)
      const code = (newCode as string) || ''
      if (code && code !== currentRoomCode.value) {
        revokeAllBlobs()
        currentRoomCode.value = code
        currentMessageList.value = []
        noMoreMessages.value = false
        if (!isAppLoading.value) chatViewIsLoading.value = true
        await getMessageList()
        await new Promise(resolve => setTimeout(resolve, 100))
        chatViewIsLoading.value = false
      }
    },
    { immediate: true },
  )

  return {
    currentMessageList,
    chatViewIsLoading,
    loadingMessages,
    noMoreMessages,
    isLoading,
    getMessageList,
    loadMoreHistory,
    sendMessage,
    handleAck,
    receiveMessage,
    formatPreviewMessage,
  }
})
