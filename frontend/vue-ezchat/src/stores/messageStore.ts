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
import i18n from '@/i18n' // 引入 i18n
import { useImageStore } from '@/stores/imageStore'

// 兼容性更好的 ID 生成器
/**
 * 生成临时消息 ID
 *
 * 业务目的：用于“本地先插入一条 sending 消息”，等服务端 ACK 后再更新状态。
 */
function generateTempId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2)
}

/**
 * MessageStore：管理消息列表（当前房间）与消息发送/接收
 *
 * 业务职责：
 * - 拉取消息列表 + 分页加载历史
 * - 发送消息：先本地插入（sending），再走 WS 发给服务端，收到 ACK 变为 sent
 * - 接收消息：如果是当前房间则插入列表，否则更新房间预览并弹通知
 * - 图片 Blob URL 管理：减少跨域/鉴权图片的重复下载成本
 */
export const useMessageStore = defineStore('message', () => {
  const router = useRouter()
  const { t } = i18n.global // 获取翻译函数
  const imageStore = useImageStore()

  // --- 状态定义 ---
  const currentMessageList = ref<Message[]>([])
  const chatViewIsLoading = ref<boolean>(false)
  const loadingMessages = ref(false)
  const noMoreMessages = ref(false)
  const isLoading = ref<boolean>(false)

  /**
   * 为消息里的图片补齐 blobUrl / blobThumbUrl
   *
   * @param messages 需要处理的消息列表
   */
  const processMessageImages = async (messages: Message[]) => {
    for (const msg of messages) {
      if (!msg.images || msg.images.length === 0) continue
      for (const img of msg.images) {
        // 原图 Blob 改为按需拉取（预览时再加载），这里只处理缩略图即可
        if (img.blobThumbUrl) continue
        imageStore.ensureThumbBlobUrl(img).then(() => {})
      }
    }
  }

  /**
   * 释放当前消息列表中所有 Blob URL
   *
   * 业务原因：切换房间时清理旧图片引用，防止内存泄漏。
   */
  const revokeAllBlobs = () => {
    currentMessageList.value.forEach(msg => {
      msg.images?.forEach(img => {
        imageStore.revokeImageBlobs(img)
      })
    })
  }

  /**
   * 重置 Store 业务数据（不重置 UI 状态）
   *
   * 业务场景：App 初始化/账号切换前，清空消息列表并释放 Blob URL，避免内存泄漏与跨房间串图。
   *
   * 注意：
   * - 这里只清“业务数据”（消息列表、分页边界、Blob 缓存）
   * - 不应重置 loading / skeleton / 过渡动画等“纯 UI 状态”，避免 UI 闪烁或状态被意外覆盖
   */
  const resetState = () => {
    revokeAllBlobs()
    imageStore.resetState()
    currentMessageList.value = []
    noMoreMessages.value = false
  }

  // --- 业务逻辑 ---
  /**
   * 构造消息去重 Key（消息指纹）
   *
   * 为什么不只用 sender + createTime：
   * - createTime 精度/格式在不同来源可能一致，存在“偶发碰撞”（同一秒内多条消息）
   * - 分页/重连时可能重复拉取同一条消息，需要更稳的指纹
   *
   * 为什么不用 objectUrl：
   * - 私有图是预签名 URL，参数会变化，同一图片会产生不同 URL，导致无法去重
   *
   * @param m 消息对象
   */
  const buildMessageKey = (m: Message): string => {
    const textPart = (m.text ?? '').trim()
    const imagePart = (m.images ?? []).map(i => i.objectName).filter(Boolean).join(',')
    // createTime 是最核心的排序与分页游标；在此基础上叠加内容指纹，降低碰撞概率
    return `${m.sender}_${m.createTime}_${m.type}_${textPart}_${imagePart}`
  }

  /**
   * 拉取消息列表（首次进入 / 分页加载）
   *
   * @param createTime 可选：用最老消息的 createTime 做分页游标
   */
  const getMessageList = async (createTime?: string) => {
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    if (!currentRoomCode.value) return

    try {
      if (!createTime) loadingMessages.value = true
      const result = await getMessageListApi({ chatCode: currentRoomCode.value, createTime: createTime || '' })
      if (result) {
        const newMessages = result.data.messageList
        const newChatRoom: ChatRoom = result.data.chatRoom
        if (newChatRoom) roomStore.updateRoomInfo(newChatRoom)
        if (newMessages.length === 0) {
          if (createTime) noMoreMessages.value = true
          return
        }
        // 去重策略：使用“消息指纹”作为 key，避免分页/重连导致重复插入
        const existingKeys = new Set(currentMessageList.value.map((m) => buildMessageKey(m)))
        const uniqueMessages = newMessages.filter((m) => {
          const key = buildMessageKey(m)
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

  /**
   * 上拉加载更多历史消息
   */
  const loadMoreHistory = async () => {
    if (loadingMessages.value || noMoreMessages.value) return
    if (currentMessageList.value.length === 0) return
    loadingMessages.value = true
    const oldestMessage = currentMessageList.value[currentMessageList.value.length - 1]
    await getMessageList(oldestMessage?.createTime)
    loadingMessages.value = false
  }

  /**
   * 发送消息（走 WebSocket）
   *
   * 业务流程：
   * 1) 本地先插入一条 sending 消息（提升响应速度）
   * 2) 通过 WS 发送到服务端
   * 3) 收到 ACK 后标记为 sent；超时则标记为 error
   */
  const sendMessage = (text: string, images: Image[] = []) => {
    const roomStore = useRoomStore()
    const userStore = useUserStore()
    const websocketStore = useWebsocketStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    const { loginUser } = storeToRefs(userStore)
    if (!currentRoomCode.value || !loginUser.value.uid) return

    const imagesCopy = [...images]
    // 为图片预处理创建临时消息对象（processMessageImages 只需要 images 字段）
    const tempMessageForProcessing: Pick<Message, 'images'> = { images: imagesCopy }
    processMessageImages([tempMessageForProcessing as Message]).then(() => {})
    const tempId = generateTempId()

    const hasText = !!text && text.trim().length > 0
    const hasImages = imagesCopy.length > 0
    const type = hasText && hasImages ? 2 : (hasImages ? 1 : 0)

    const newMessage: Message = {
      sender: loginUser.value.uid,
      chatCode: currentRoomCode.value,
      type,
      text: text,
      images: imagesCopy,
      createTime: new Date().toISOString(),
      tempId: tempId,
      status: 'sending'
    }
    currentMessageList.value.unshift(newMessage)
    // 发送给服务端的 WS 载荷（服务端会根据 text/images 计算消息 type）
    const payload = { chatCode: currentRoomCode.value, text: text, images: imagesCopy, tempId: tempId, sender: loginUser.value.uid }
    websocketStore.sendData(payload)
    // 超时兜底：防止网络波动导致 ACK 永远不到，UI 需要可见的失败态
    setTimeout(() => {
      const msg = currentMessageList.value.find(m => m.tempId === tempId)
      if (msg && msg.status === 'sending') msg.status = 'error'
    }, 10000)
  }

  /**
   * 处理服务端 ACK：将临时消息标记为 sent
   */
  const handleAck = (tempId: string) => {
    const msg = currentMessageList.value.find(m => m.tempId === tempId)
    if (msg) msg.status = 'sent'
  }

  /**
   * 接收服务端推送的消息
   *
   * @param message 服务端推送的消息对象（已包含 type/images）
   */
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
    const sender = chat?.chatMembers?.find((m) => m.uid === message.sender)
    if (chat && sender) {
      // 通知预览文案：图片消息用 [画像] 标签避免空文本
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
  const formatPreviewMessage = (message?: Message | null): string => {
    // 初始化阶段 chatList-only 可能没有 lastMessage，需要容错
    if (!message) return `[${t('chat.new_message')}]`
    let result = message.text || ''
    if (message.images && message.images.length > 0) {
      // 使用翻译后的 [画像] 标签
      result += `[${t('chat.image')}]`.repeat(message.images.length)
    }
    // 使用翻译后的 [无消息] 占位符
    return result || `[${t('chat.new_message')}]`
  }

  watch(
    () => [router.currentRoute.value.params.chatCode, useAppStore().isAppInitializing] as const,
    async ([newCode, isAppInitializing]) => {
      const roomStore = useRoomStore()
      const appStore = useAppStore()
      const { currentRoomCode } = storeToRefs(roomStore)
      const { isAppLoading } = storeToRefs(appStore)
      // 初始化期间不拉取消息：避免“先拉取 → 又被 reset 清空”或 token 未就绪导致数据异常
      if (isAppInitializing) return
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
    resetState,
  }
})
