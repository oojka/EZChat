// =========================================
// 导入依赖
// =========================================
import { defineStore, storeToRefs } from 'pinia'           // Pinia 状态管理
import type { ChatRoom, Image, Message } from '@/type'    // 类型定义
import { ref, watch } from 'vue'                          // Vue 响应式 API
import { useRouter } from 'vue-router'                    // 路由管理
import { getMessageListApi, syncMessageApi } from '@/api/Message.ts'      // 消息相关 API
import { useRoomStore } from '@/stores/roomStore.ts'      // 房间状态管理
import { useAppStore } from '@/stores/appStore.ts'        // 应用全局状态
import { useUserStore } from '@/stores/userStore.ts'      // 用户状态管理
import { useWebsocketStore } from '@/stores/websocketStore.ts' // WebSocket 状态管理
import { showMessageNotification } from '@/components/notification.ts' // 消息通知组件
import i18n from '@/i18n'                               // 国际化
import { useImageStore } from '@/stores/imageStore'     // 图片状态管理
import { isValidMessage } from '@/utils/validators'     // [NEW] 引入消息验证器

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

function getLocalISOString(date = new Date()) {
  const pad = (num: number, size = 2) => String(num).padStart(size, '0')
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hours = pad(date.getHours())
  const minutes = pad(date.getMinutes())
  const seconds = pad(date.getSeconds())
  const milliseconds = pad(date.getMilliseconds(), 3)
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const absOffset = Math.abs(offset)
  const offsetHours = pad(Math.floor(absOffset / 60))
  const offsetMinutes = pad(absOffset % 60)

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${milliseconds}${sign}${offsetHours}:${offsetMinutes}`
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
   * 历史消息加载中状态
   * - true: loadMoreHistory 正在拉取更早的历史消息
   * - false: 非历史拉取
   * 用途：避免历史拉取触发“新消息”提示
   */
  const isLoadingHistory = ref(false)

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
  // =========================================
  const isLoading = ref<boolean>(false)

  /**
   * 消息同步中状态
   * - true: 正在检测断层并补齐消息
   * - false: 正常状态
   * 用途：在消息列表底部显示加载动画
   */
  const isSyncing = ref<boolean>(false)

  // =========================================
  // 图片处理函数
  // =========================================

  /**
   * 为消息里的图片补齐 blobUrl / blobThumbUrl
   *
   * 业务目的：
   * - 将图片的 objectUrl（预签名URL）转换为 blobUrl，避免重复下载和跨域问题
   * - 实现图片懒加载：缩略图立即加载，原图按需加载
   *
   * 技术实现：
   * - 遍历消息中的图片数组
   * - 检查是否已有 blobThumbUrl，避免重复处理
   * - 调用 imageStore.ensureThumbBlobUrl 异步获取缩略图 Blob URL
   * - 原图 Blob URL 按需加载（用户点击预览时再加载）
   *
   * 性能优化：
   * - 使用异步处理，不阻塞主线程
   * - 避免重复处理已缓存的图片
   * - 缩略图优先加载，提升用户体验
   *
   * @param {Message[]} messages - 需要处理的消息列表
   */
  /**
   * 为消息里的图片补齐 blobUrl / blobThumbUrl
   */
  const processMessageImages = async (messages: Message[]) => {
    for (const msg of messages) {
      // 使用 'images' in msg 检查是否包含 images 属性
      if (!('images' in msg) || !msg.images || msg.images.length === 0) continue
      for (const img of msg.images) {
        if (img.blobThumbUrl) continue
        imageStore.ensureThumbBlobUrl(img).then(() => { })
      }
    }
  }

  /**
   * 释放当前消息列表中所有 Blob URL
   *
   * 业务原因：
   * - 切换房间时清理旧图片引用，防止内存泄漏
   * - Blob URL 占用浏览器内存，不及时释放会导致内存增长
   * - 避免跨房间图片缓存干扰
   *
   * 技术实现：
   * - 遍历当前消息列表中的所有消息
   * - 遍历每条消息中的所有图片
   * - 调用 imageStore.revokeImageBlobs 释放 Blob URL
   *
   * 调用时机：
   * - 切换聊天房间时
   * - 用户退出登录时
   * - 应用重置状态时
   */
  const revokeAllBlobs = () => {
    currentMessageList.value.forEach(msg => {
      if ('images' in msg && msg.images) {
        msg.images.forEach(img => {
          imageStore.revokeImageBlobs(img)
        })
      }
    })
  }

  /**
   * 重置 Store 业务数据（不重置 UI 状态）
   *
   * 业务场景：
   * - App 初始化前：清空旧数据，准备加载新数据
   * - 账号切换前：防止跨账号数据泄露
   * - 用户退出登录时：清理用户相关数据
   *
   * 设计原则：
   * - 只清"业务数据"（消息列表、分页边界、Blob 缓存）
   * - 不重置"纯 UI 状态"（loading / skeleton / 过渡动画）
   * - 避免 UI 闪烁或状态被意外覆盖
   *
   * 执行步骤：
   * 1. 释放所有 Blob URL，防止内存泄漏
   * 2. 重置图片存储状态
   * 3. 清空当前消息列表
   * 4. 重置分页边界标志
   */
  const resetState = () => {
    revokeAllBlobs()
    imageStore.resetState()
    currentMessageList.value = []
    noMoreMessages.value = false
  }

  // =========================================
  // 业务逻辑函数
  // =========================================

  /**
   * 构造消息去重 Key（消息指纹）
   *
   * 业务目的：
   * - 为每条消息生成唯一标识，用于消息去重
   * - 防止分页加载、重连等场景下重复插入同一条消息
   *
   * 设计考虑：
   * 为什么不只用 sender + createTime？
   * - createTime 精度/格式在不同来源可能一致，存在"偶发碰撞"（同一秒内多条消息）
   * - 分页/重连时可能重复拉取同一条消息，需要更稳定的指纹
   *
   * 为什么不用 objectUrl？
   * - 私有图是预签名 URL，参数会变化，同一图片会产生不同 URL，导致无法去重
   * - 使用 objectName（对象存储中的唯一名称）作为图片标识
   *
   * Key 组成：
   * - sender: 发送者ID，区分不同用户
   * - createTime: 消息创建时间，核心排序与分页游标
   * - type: 消息类型（0=文本,1=图片,2=混合）
   * - textPart: 文本内容（去空格）
   * - imagePart: 图片 objectName 列表（逗号分隔）
   *
   * @param {Message} m - 消息对象
   * @returns {string} 消息唯一标识
   */
  const buildMessageKey = (m: Message): string => {
    const textPart = (m.text ?? '').trim()
    const images = 'images' in m ? m.images : []
    const imagePart = (images ?? []).map(i => i.imageName).filter(Boolean).join(',')
    return `${m.sender}_${m.createTime}_${m.type}_${textPart}_${imagePart}`
  }

  /**
   * 消息列表排序函数
   *
   * 排序规则（倒序，Index 0 为最新）：
   * 1. 都有 seqId：按 seqId 倒序（大号在前）
   * 2. 一个有 seqId 一个没有：没有 seqId 的（本地 pending 消息）排在前面（视为更新）
   * 3. 都没有 seqId：按 createTime 倒序
   *
   * @param list 待排序的消息列表
   */
  const sortMessages = (list: Message[]): Message[] => {
    return list.sort((a, b) => {
      // 1. 都有 seqId：按 seqId 倒序
      if (a.seqId !== undefined && b.seqId !== undefined) {
        return b.seqId - a.seqId
      }
      // 2. 一个有 seqId 一个没有
      // 本地发送的消息（status='sending'/'error'）通常没有 seqId，应该排在已有 seqId 消息（历史消息）的前面
      if (a.seqId === undefined && b.seqId !== undefined) return -1 // a 排在 b 前面
      if (a.seqId !== undefined && b.seqId === undefined) return 1  // b 排在 a 前面

      // 3. 都没有 seqId：按 createTime 倒序
      const timeA = new Date(a.createTime).getTime()
      const timeB = new Date(b.createTime).getTime()
      return timeB - timeA
    })
  }

  /**
   * 拉取消息列表（首次进入 / 分页加载）
   *
   * 业务场景：
   * 1. 首次进入房间：加载最新消息（createTime 为空）
   * 2. 上拉加载更多：加载历史消息（createTime 为最老消息的时间）
   *
   * 执行流程：
   * 1. 检查当前房间代码，确保在有效房间中
   * 2. 设置加载状态（首次加载时显示加载动画）
   * 3. 调用 API 获取消息列表
   * 4. 更新房间信息（如成员数、最后消息等）
   * 5. 消息去重处理（防止重复消息）
   * 6. 处理消息中的图片（生成 Blob URL）
   * 7. 更新消息列表（首次加载替换，分页加载追加）
   *
   * 错误处理：
   * - 网络错误：抛出异常，由调用方处理
   * - 空数据：设置 noMoreMessages 标志，避免重复请求
   *
   * @param {number} [cursorSeqId] - 可选：用最老消息的 seqId 做分页游标，为空则查最新
   * @throws {Error} 加载失败时抛出错误
   */
  const getMessageList = async (cursorSeqId?: number) => {
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    if (!currentRoomCode.value) return


    try {
      // 首次加载时显示加载动画，分页加载时不显示（避免UI跳动）
      if (!cursorSeqId) loadingMessages.value = true

      // 调用 API 获取消息列表
      // cursorSeqId: 空=最新消息; 非空=加载该 ID 之前的历史消息
      const result = await getMessageListApi({ chatCode: currentRoomCode.value, cursorSeqId })

      if (result) {
        const newMessages = result.data.messageList
        const newChatRoom: ChatRoom = result.data.chatRoom

        // 更新房间信息（成员数、最后消息时间等）
        if (newChatRoom) roomStore.updateRoomInfo(newChatRoom)

        // 处理空数据情况
        if (newMessages.length === 0) {
          if (cursorSeqId) noMoreMessages.value = true  // 分页加载时无数据，标记为已加载完
          return
        }

        // 去重策略：使用"消息指纹"作为 key，避免分页/重连导致重复插入
        const existingKeys = new Set(currentMessageList.value.map((m) => buildMessageKey(m)))
        const uniqueMessages = newMessages.filter((m) => {
          const key = buildMessageKey(m)
          // 只有当消息是新的（无 createTime 是异常, 但这里重点是不重复）或是历史消息时
          return !existingKeys.has(key)
        })

        // 去重后仍无新消息
        if (uniqueMessages.length === 0) {
          if (cursorSeqId) noMoreMessages.value = true
          return
        }

        // 处理消息中的图片（生成缩略图 Blob URL）
        await processMessageImages(uniqueMessages)

        // 更新消息列表：首次加载替换，分页加载追加
        if (!cursorSeqId) {
          currentMessageList.value = sortMessages(uniqueMessages)  // 首次进入，替换整个列表并排序
        } else {
          // 分页加载，追加到末尾（历史消息），由于是追加更旧的消息，
          // 理论上 uniqueMessages 都是更旧的，但为了保险，合并后整体重排
          const merged = [...currentMessageList.value, ...uniqueMessages]
          currentMessageList.value = sortMessages(merged)
        }
      }
    } catch {
      throw new Error('Failed to load messages')
    } finally {
      // 无论成功失败，都要清除加载状态
      loadingMessages.value = false
    }
  }

  /**
   * 上拉加载更多历史消息
   *
   * 业务场景：
   * - 用户滚动到消息列表顶部时，自动加载更早的历史消息
   * - 实现无限滚动（Infinite Scroll）体验
   *
   * 执行条件检查：
   * 1. 是否正在加载中（loadingMessages.value）
   * 2. 是否已加载完所有消息（noMoreMessages.value）
   * 3. 当前是否有消息（用于获取分页游标）
   *
   * 执行流程：
   * 1. 检查加载条件，不满足则直接返回
   * 2. 设置加载状态，防止重复加载
   * 3. 获取最老消息的 createTime 作为分页游标
   * 4. 调用 getMessageList 加载更早的消息
   * 5. 清除加载状态
   *
   * 设计要点：
   * - 防抖：通过 loadingMessages 状态防止重复调用
   * - 边界处理：检查 noMoreMessages 避免无效请求
   * - 游标管理：使用最老消息的时间作为分页依据
   */
  const loadMoreHistory = async () => {
    // 检查加载条件
    if (loadingMessages.value || noMoreMessages.value) return
    if (currentMessageList.value.length === 0) return

    // 设置加载状态
    loadingMessages.value = true
    isLoadingHistory.value = true

    // 获取分页游标（最老消息的 seqId）
    // currentMessageList 是倒序（新->旧），所以最后一项是最老的
    const oldestMessage = currentMessageList.value[currentMessageList.value.length - 1]

    try {
      if (oldestMessage && oldestMessage.seqId) {
        // 加载更多消息
        await getMessageList(oldestMessage.seqId)
      } else {
        console.warn('loadMoreHistory: Oldest message has no seqId', oldestMessage)
        noMoreMessages.value = true // 无法继续加载，标记为结束
      }
    } finally {
      // 清除加载状态
      loadingMessages.value = false
      isLoadingHistory.value = false
    }
  }

  /**
   * 发送消息（走 WebSocket）
   *
   * 核心业务流程（优化用户体验）：
   * 1. 本地先插入一条 sending 状态的消息（立即显示，提升响应速度）
   * 2. 通过 WebSocket 发送到服务端（异步传输）
   * 3. 收到服务端 ACK 后标记为 sent（确认发送成功）
   * 4. 超时未收到 ACK 则标记为 error（提供失败反馈）
   *
   * 消息类型计算规则：
   * - type 0: 纯文本消息（有文本，无图片）
   * - type 1: 纯图片消息（无文本，有图片）
   * - type 2: 混合消息（既有文本又有图片）
   *
   * 执行步骤：
   * 1. 验证发送条件（当前房间、用户登录状态）
   * 2. 复制图片数组（避免引用修改）
   * 3. 预处理图片（生成缩略图 Blob URL）
   * 4. 生成临时消息 ID（用于ACK匹配）
   * 5. 计算消息类型
   * 6. 构建消息对象并插入列表（sending状态）
   * 7. 通过 WebSocket 发送到服务端
   * 8. 设置超时检查（10秒后未收到ACK标记为error）
   *
   * 设计要点：
   * - 乐观更新：先本地显示，再异步发送，提升用户体验
   * - 状态管理：sending → sent/error 状态流转
   * - 超时兜底：防止网络问题导致消息卡在sending状态
   * - 图片预处理：提前处理图片，避免发送后无法显示
   *
   * @param {string} text - 消息文本内容
   * @param {Image[]} [images=[]] - 消息图片数组，默认为空
   */
  const sendMessage = (text: string, images: Image[] = []) => {
    const roomStore = useRoomStore()
    const userStore = useUserStore()
    const websocketStore = useWebsocketStore()
    const { currentRoomCode } = storeToRefs(roomStore)

    // 验证发送条件
    const currentUserId = userStore.getCurrentUserId()
    if (!currentRoomCode.value || !currentUserId) return

    // 复制图片数组（避免直接修改传入的引用）
    const imagesCopy = [...images]

    // 为图片预处理创建临时消息对象（processMessageImages 只需要 images 字段）
    // 异步处理图片，不阻塞消息发送流程
    // [FIX] 不再强制构造 Message 对象，而是直接处理图片数组，避免因 Message 联合类型（如 MemberJoinMessage 无 images）导致的 createType 错误
    if (imagesCopy.length > 0) {
      imagesCopy.forEach(img => {
        if (!img.blobThumbUrl) {
          imageStore.ensureThumbBlobUrl(img).then(() => { })
        }
      })
    }

    // 生成临时消息ID（用于服务端ACK匹配）
    const tempId = generateTempId()

    // 计算消息类型
    const hasText = !!text && text.trim().length > 0
    const hasImages = imagesCopy.length > 0
    const type = hasText && hasImages ? 2 : (hasImages ? 1 : 0)

    // 构建新消息对象
    const newMessage: Message = {
      sender: currentUserId,
      chatCode: currentRoomCode.value,
      type,
      text: text,
      images: imagesCopy,
      createTime: getLocalISOString(),
      tempId: tempId,
      status: 'sending'  // 初始状态：发送中
    }

    // 乐观更新：立即插入到消息列表最前面（最新消息在最前面）
    currentMessageList.value.unshift(newMessage)

    // 发送给服务端的 WebSocket 载荷
    // 注意：服务端会根据 text/images 重新计算消息 type，这里发送的type仅供参考
    const payload = {
      chatCode: currentRoomCode.value,
      text: text,
      images: imagesCopy,
      tempId: tempId,
      sender: currentUserId
    }
    websocketStore.sendData(payload)

    // 超时兜底：防止网络波动导致 ACK 永远不到
    // 优化策略：先通过 HTTP 同步拉取验证消息是否已到达服务端，避免误报
    setTimeout(async () => {
      const msg = currentMessageList.value.find(m => m.tempId === tempId)
      if (!msg || msg.status !== 'sending') return // 已收到 ACK 或已处理，无需继续

      try {
        // 尝试通过 HTTP 同步接口拉取最新消息
        const latestSynced = currentMessageList.value.find(m => m.seqId !== undefined)
        if (latestSynced?.seqId) {
          const res = await syncMessageApi({
            chatCode: currentRoomCode.value,
            lastSeqId: latestSynced.seqId
          })

          if (res?.data) {
            // 在同步结果中查找匹配的消息（通过 sender + chatCode + 相近的 createTime）
            const msgTime = new Date(msg.createTime).getTime()
            const matchedMsg = res.data.find(m =>
              m.sender === msg.sender &&
              m.chatCode === msg.chatCode &&
              Math.abs(new Date(m.createTime).getTime() - msgTime) < 5000 // 5秒内的消息
            )

            if (matchedMsg) {
              // 消息已成功发送到服务端，更新状态
              msg.status = 'sent'
              msg.seqId = matchedMsg.seqId

              // 同步成功后也更新房间预览（最后消息、时间）
              roomStore.updateRoomPreview(msg)
              return
            }
          }
        }
      } catch (e) {
        console.warn('[MessageStore] ACK 超时后 HTTP 验证失败:', e)
      }

      // HTTP 验证也未找到，确认为发送失败
      if (msg.status === 'sending') {
        msg.status = 'error'
      }
    }, 10000)  // 10秒超时
  }

  /**
   * 处理服务端 ACK：将临时消息标记为 sent
   *
   * 业务场景：
   * - 服务端成功接收并处理消息后，会通过 WebSocket 返回 ACK
   * - 客户端收到 ACK 后，将对应消息的状态从 sending 改为 sent
   *
   * 执行流程：
   * 1. 根据 tempId 在消息列表中查找对应消息
   * 2. 如果找到，将状态更新为 sent（发送成功）
   *
   * 设计要点：
   * - tempId 匹配：使用发送时生成的临时ID进行匹配
   * - 状态更新：只更新状态字段，不影响其他消息属性
   * - 容错处理：找不到对应消息时静默失败（可能已被超时处理）
   *
   * @param {string} tempId - 临时消息ID（发送时生成）
   * @param {number} seqId - 服务端生成的序列号
   */
  const handleAck = (tempId: string, seqId: number) => {
    const msg = currentMessageList.value.find(m => m.tempId === tempId)
    if (msg) {
      msg.status = 'sent'  // 标记为发送成功
      msg.seqId = seqId    // 更新 seqId (用于后续断层检测)

      // ACK 收到后更新房间预览（最后消息、时间），确保列表排序更新
      const roomStore = useRoomStore()
      roomStore.updateRoomPreview(msg)
    }
  }

  /**
   * 接收服务端推送的消息
   *
   * 业务场景：
   * - 实时接收其他用户发送的消息
   * - 通过 WebSocket 推送，实现即时通讯
   *
   * 消息处理逻辑分支：
   * 1. 当前房间的消息：
   *    - 处理消息中的图片（生成 Blob URL）
   *    - 排除自己发送的消息（避免重复显示）
   *    - 插入到当前消息列表最前面
   *    - 更新房间预览信息（最后消息时间等）
   *
   * 2. 其他房间的消息：
   *    - 查找发送者和房间信息
   *    - 格式化预览消息（图片消息添加标签）
   *    - 显示桌面通知（浏览器通知）
   *    - 更新房间预览信息（未读消息数等）
   *    - 如果房间信息不存在，重新初始化房间列表
   *
   * 设计要点：
   * - 图片预处理：收到消息立即处理图片，避免显示延迟
   * - 去重逻辑：排除自己发送的消息（已在发送时本地插入）
   * - 通知管理：非当前房间的消息显示桌面通知
   * - 状态同步：及时更新房间预览信息
   *
   *
   * @param {unknown} rawMessage - 服务端推送的原始消息数据（loose type）
   */
  const receiveMessage = async (rawMessage: unknown) => {
    // 1. 运行时校验：确保消息结构合法
    if (!isValidMessage(rawMessage)) {
      // console.warn('[MessageStore] Invalid message format received, ignored:', rawMessage)
      return
    }


    const message = rawMessage as Message // 校验通过，安全断言
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)



    // 分支1：当前房间的消息
    if (message.chatCode === currentRoomCode.value) {
      const userStore = useUserStore()

      // 排除自己发送的消息（避免重复显示）
      if (message.sender === userStore.getCurrentUserId()) return

      // 预处理消息中的图片（生成缩略图 Blob URL）
      await processMessageImages([message])

      // Gap Detection: 检查 seqId 是否连续
      // 找到当前列表最新的已同步消息 (跳过本地 sending 消息)
      const latestSynced = currentMessageList.value.find(m => m.seqId !== undefined)

      // 分支2：检测到 seqId 不连续
      // 如果有最新消息，且新消息 seqId > 最新 + 1，说明中间有缺漏
      if (latestSynced?.seqId && message.seqId && message.seqId > latestSynced.seqId + 1) {
        console.warn(`[MessageStore] Gap detected: Last=${latestSynced.seqId}, New=${message.seqId}. Syncing...`)
        try {
          isSyncing.value = true // 开始同步
          // 调用 sync 接口补齐断层
          const res = await syncMessageApi({
            chatCode: currentRoomCode.value,
            lastSeqId: latestSynced.seqId
          })

          if (res && res.data) {
            const missedMessages = res.data
            // 预处理补齐的消息图片
            await processMessageImages(missedMessages)

            // 合并: 历史 + 补齐 + 当前新消息 (新消息可能包含在 sync 结果中，也可能不包含)
            const combined = [...currentMessageList.value, ...missedMessages, message]

            // 去重
            const existingKeys = new Set<string>()
            const uniqueMessages = combined.filter(m => {
              const key = buildMessageKey(m)
              if (existingKeys.has(key)) return false
              existingKeys.add(key)
              return true
            })

            // 排序并更新
            currentMessageList.value = sortMessages(uniqueMessages)

            // 更新预览
            if (uniqueMessages.length > 0) {
              roomStore.updateRoomPreview(uniqueMessages[0]!) // 最新的一条
            }
            return
          }
    } catch (syncError) {
          console.error('[MessageStore] Sync failed, falling back to full refresh', syncError)
          await getMessageList()
          return
        } finally {
          isSyncing.value = false // 同步结束
        }
      }

      // 插入到当前消息列表最前面（最新消息在最前面）
      // 使用 sortMessages 重新排序，确保顺序正确（防止 WebSocket 乱序或 seqId 补齐后的位置调整）
      const newList = [message, ...currentMessageList.value]
      currentMessageList.value = sortMessages(newList)

      // 更新房间预览信息（最后消息时间、预览内容等）
      roomStore.updateRoomPreview(message)
      return
    }

    // 分支3：其他房间的消息
    const chat = roomStore.getRoomByCode(message.chatCode)
    const sender = chat?.chatMembers?.find((m) => m.uid === message.sender)

    if (chat && sender) {
      // 格式化预览消息：图片消息用 [画像] 标签避免空文本
      message.text = formatPreviewMessage(message)

      // 显示桌面通知（浏览器通知）
      showMessageNotification(message, sender, chat.chatName)

      // 更新房间预览信息（未读消息数、最后消息等）
      roomStore.updateRoomPreview(message)
    } else {
      // 房间或发送者信息不存在，重新初始化房间列表
      await roomStore.initRoomList()
    }
  }

  const formatPreviewMessage = (message?: Message | null): string => {
    if (!message) return `[${t('chat.new_message')}]`

    let result = message.text || ''

    // 处理图片消息：追加图片标签
    if ('images' in message && message.images && message.images.length > 0) {
      result += `[${t('chat.image')}]`.repeat(message.images.length)
    }

    return result || `[${t('chat.new_message')}]`
  }

  // =========================================
  // 路由监听器
  // =========================================

  /**
   * 路由参数监听器：监听聊天房间切换并自动加载消息
   *
   * 监听依赖：
   * 1. router.currentRoute.value.params.chatCode - 路由中的房间代码参数
   * 2. useAppStore().isAppInitializing - 应用初始化状态
   * 3. router.currentRoute.value.path - 当前路由路径
   *
   * 业务逻辑：
   * 1. 应用初始化期间不加载消息（避免数据被重置或token未就绪）
   * 2. 只监听 /chat 路由下的房间切换（排除 /Join 等路由）
   * 3. 当房间代码发生变化时，执行消息加载流程
   *
   * 消息加载流程：
   * 1. 释放旧房间的图片 Blob URL（防止内存泄漏）
   * 2. 更新当前房间代码状态
   * 3. 清空当前消息列表（准备加载新房间消息）
   * 4. 重置分页边界标志（允许加载历史消息）
   * 5. 显示聊天区域加载骨架屏（提升用户体验）
   * 6. 加载新房间的消息列表
   * 7. 短暂延迟后隐藏加载骨架屏（避免闪烁）
   *
   * 设计要点：
   * - 立即执行（immediate: true）：组件挂载时立即检查当前路由
   * - 路由过滤：只处理 /chat 路由，避免误触发
   * - 状态检查：确保应用已初始化完成
   * - 防抖处理：通过状态检查避免重复加载
   * - 资源清理：切换房间时及时释放图片资源
   */
  watch(
    () => [router.currentRoute.value.params.chatCode, useAppStore().isAppInitializing, router.currentRoute.value.path] as const,
    async ([newCode, isAppInitializing, currentPath]) => {
      const roomStore = useRoomStore()
      const appStore = useAppStore()
      const { currentRoomCode } = storeToRefs(roomStore)
      const { isAppLoading } = storeToRefs(appStore)

      // 检查1：应用初始化期间不拉取消息
      // 避免"先拉取 → 又被 reset 清空"或 token 未就绪导致数据异常
      if (isAppInitializing) return

      // 检查2：只监听 /chat 路由下的 chatCode 参数变化
      // 避免在 /Join/:chatCode 等路由下触发消息加载
      if (!currentPath.startsWith('/chat')) return

      const code = (newCode as string) || ''

      // 检查3：房间代码发生变化时才执行加载
      if (code && code !== currentRoomCode.value) {
        // 步骤1：释放旧房间的图片 Blob URL（防止内存泄漏）
        revokeAllBlobs()

        // 步骤2：更新当前房间代码状态
        currentRoomCode.value = code

        // 步骤3：清空当前消息列表（准备加载新房间消息）
        currentMessageList.value = []

        // 步骤4：重置分页边界标志（允许加载历史消息）
        noMoreMessages.value = false

        // 步骤5：显示聊天区域加载骨架屏（提升用户体验）
        if (!isAppLoading.value) chatViewIsLoading.value = true

        // 步骤6：加载新房间的消息列表
        await getMessageList()

        // 步骤7：短暂延迟后隐藏加载骨架屏（避免闪烁）
        await new Promise(resolve => setTimeout(resolve, 100))
        chatViewIsLoading.value = false
      }
    },
    { immediate: true },  // 组件挂载时立即执行一次
  )

  // =========================================
  // 导出接口
  // =========================================

  /**
   * WebSocket 重连后同步消息
   *
   * 业务目的：
   * - 补齐断开期间可能遗漏的新消息
   * - 验证 sending 状态的消息是否已成功发送到服务端
   *
   * 执行流程：
   * 1. 获取当前房间最新的已同步消息 seqId
   * 2. 调用 sync 接口拉取断开期间的新消息
   * 3. 检查 sending 状态的消息是否在同步结果中（说明已发送成功）
   * 4. 合并消息并更新列表
   */
  const syncAfterReconnect = async () => {
    const roomStore = useRoomStore()
    const { currentRoomCode } = storeToRefs(roomStore)
    if (!currentRoomCode.value) return

    try {
      // 找到最新的已同步消息
      const latestSynced = currentMessageList.value.find(m => m.seqId !== undefined)
      if (!latestSynced?.seqId) {
        // 没有已同步的消息，直接重新加载
        await getMessageList()
        return
      }

      isSyncing.value = true

      // 调用 sync 接口拉取新消息
      const res = await syncMessageApi({
        chatCode: currentRoomCode.value,
        lastSeqId: latestSynced.seqId
      })

      if (res?.data && res.data.length > 0) {
        const syncedMessages = res.data

        // 处理图片
        await processMessageImages(syncedMessages)

        // 检查 sending 状态的消息是否在同步结果中
        const sendingMessages = currentMessageList.value.filter(m => m.status === 'sending')
        for (const msg of sendingMessages) {
          const msgTime = new Date(msg.createTime).getTime()
          const matchedMsg = syncedMessages.find(m =>
            m.sender === msg.sender &&
            m.chatCode === msg.chatCode &&
            Math.abs(new Date(m.createTime).getTime() - msgTime) < 5000
          )

          if (matchedMsg) {
            // 消息已成功发送，更新状态
            msg.status = 'sent'
            msg.seqId = matchedMsg.seqId
          }
        }

        // 合并消息并去重
        const combined = [...currentMessageList.value, ...syncedMessages]
        const existingKeys = new Set<string>()
        const uniqueMessages = combined.filter(m => {
          const key = buildMessageKey(m)
          if (existingKeys.has(key)) return false
          existingKeys.add(key)
          return true
        })

        currentMessageList.value = sortMessages(uniqueMessages)

        // 更新房间预览
        if (uniqueMessages.length > 0) {
          roomStore.updateRoomPreview(uniqueMessages[0]!)
        }
      }
    } catch (e) {
      console.error('[MessageStore] Reconnect sync failed:', e)
    } finally {
      isSyncing.value = false
    }
  }

  return {
    // 状态
    currentMessageList,     // 当前房间的消息列表（响应式）
    chatViewIsLoading,      // 聊天视图加载状态（骨架屏控制）
    loadingMessages,        // 消息加载中状态（分页加载控制）
    isLoadingHistory,       // 历史消息加载中状态
    noMoreMessages,         // 没有更多消息标志（分页边界控制）
    isLoading,              // 通用加载状态（按钮禁用控制）
    isSyncing,              // 消息同步中状态

    // 消息操作
    getMessageList,         // 拉取消息列表（首次/分页）
    loadMoreHistory,        // 上拉加载更多历史消息
    sendMessage,            // 发送消息（WebSocket）

    // WebSocket 事件处理
    handleAck,              // 处理服务端 ACK（消息发送确认）
    receiveMessage,         // 接收服务端推送的消息
    syncAfterReconnect,     // 重连后同步消息

    // 工具函数
    formatPreviewMessage,   // 格式化预览消息（国际化适配）

    // 状态管理
    resetState,             // 重置 Store 业务数据
  }
})
