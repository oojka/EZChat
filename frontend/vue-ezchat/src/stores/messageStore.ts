// =========================================
// 导入依赖
// =========================================
import {defineStore, storeToRefs} from 'pinia'           // Pinia 状态管理
import type {ChatRoom, Image, Message} from '@/type'    // 类型定义
import {ref, watch} from 'vue'                          // Vue 响应式 API
import {useRouter} from 'vue-router'                    // 路由管理
import {getMessageListApi} from '@/api/Message.ts'      // 消息相关 API
import {useRoomStore} from '@/stores/roomStore.ts'      // 房间状态管理
import {useAppStore} from '@/stores/appStore.ts'        // 应用全局状态
import {useUserStore} from '@/stores/userStore.ts'      // 用户状态管理
import {useWebsocketStore} from '@/stores/websocketStore.ts' // WebSocket 状态管理
import {showMessageNotification} from '@/components/notification.ts' // 消息通知组件
import i18n from '@/i18n'                               // 国际化
import { useImageStore } from '@/stores/imageStore'     // 图片状态管理

// =========================================
// 工具函数
// =========================================

/**
 * 生成临时消息 ID
 *
 * 业务目的：
 * - 用于"本地先插入一条 sending 消息"的临时标识
 * - 等待服务端 ACK 后，通过此 ID 找到对应消息并更新状态
 *
 * 技术实现：
 * - 使用时间戳 + 随机数组合，确保唯一性
 * - 使用 base36 编码，缩短 ID 长度
 *
 * @returns {string} 临时消息 ID
 */
function generateTempId() {
  return Date.now().toString(36) + Math.random().toString(36).substring(2)
}

// =========================================
// MessageStore 定义
// =========================================

/**
 * MessageStore：管理消息列表（当前房间）与消息发送/接收
 *
 * 核心业务职责：
 * 1. 消息列表管理：拉取消息列表 + 分页加载历史
 * 2. 消息发送流程：先本地插入（sending），再走 WS 发给服务端，收到 ACK 变为 sent
 * 3. 消息接收处理：如果是当前房间则插入列表，否则更新房间预览并弹通知
 * 4. 图片资源管理：Blob URL 缓存，减少跨域/鉴权图片的重复下载成本
 * 5. 消息去重与状态管理：防止重复消息，管理发送状态（sending/sent/error）
 *
 * 设计原则：
 * - 单一职责：只管理当前房间的消息相关状态
 * - 响应式设计：所有状态都是响应式的，UI 自动更新
 * - 错误处理：完善的错误处理和状态恢复机制
 * - 性能优化：图片懒加载、消息去重、Blob URL 缓存
 */
export const useMessageStore = defineStore('message', () => {
  // =========================================
  // 依赖注入
  // =========================================
  const router = useRouter()                    // 路由实例，用于监听路由变化
  const { t } = i18n.global                    // 国际化翻译函数
  const imageStore = useImageStore()           // 图片存储，用于管理图片 Blob URL

  // =========================================
  // 状态定义
  // =========================================
  
  /**
   * 当前房间的消息列表
   * - 按时间倒序排列（最新的在最前面）
   * - 包含发送中、已发送、错误等状态的消息
   */
  const currentMessageList = ref<Message[]>([])
  
  /**
   * 聊天视图加载状态
   * - true: 显示聊天区域的加载骨架屏
   * - false: 正常显示消息列表
   * 用途：首次进入房间或切换房间时显示加载效果
   */
  const chatViewIsLoading = ref<boolean>(false)
  
  /**
   * 消息加载中状态（用于分页加载）
   * - true: 正在加载更多历史消息
   * - false: 加载完成或未在加载
   * 用途：控制上拉加载更多的加载动画
   */
  const loadingMessages = ref(false)
  
  /**
   * 没有更多消息标志
   * - true: 已加载所有历史消息，无需继续加载
   * - false: 还有更多历史消息可以加载
   * 用途：优化性能，避免不必要的分页请求
   */
  const noMoreMessages = ref(false)
  
  /**
   * 通用加载状态
   * - true: 正在执行某个异步操作
   * - false: 操作完成
   * 用途：控制按钮禁用状态、加载动画等
   */
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
    () => [router.currentRoute.value.params.chatCode, useAppStore().isAppInitializing, router.currentRoute.value.path] as const,
    async ([newCode, isAppInitializing, currentPath]) => {
      const roomStore = useRoomStore()
      const appStore = useAppStore()
      const { currentRoomCode } = storeToRefs(roomStore)
      const { isAppLoading } = storeToRefs(appStore)
      
      // 初始化期间不拉取消息：避免“先拉取 → 又被 reset 清空”或 token 未就绪导致数据异常
      if (isAppInitializing) return
      
      // 只监听 /chat 路由下的 chatCode 参数变化
      // 避免在 /Join/:chatCode 等路由下触发消息加载
      if (!currentPath.startsWith('/chat')) return
      
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
