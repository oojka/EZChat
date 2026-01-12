import { defineStore, storeToRefs } from 'pinia'
import { computed, ref } from 'vue'
import { useWebsocket } from '@/WS/useWebsocket.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useFriendStore } from '@/stores/friendStore.ts'
import { ElMessage, ElNotification } from 'element-plus'
import type { ChatMember, FriendRequest, AckPayload, Image } from '@/type'
import { useConfigStore } from '@/stores/configStore.ts'
import { useImageStore } from '@/stores/imageStore.ts'
import i18n from '@/i18n'
import { isTokenExpired } from '@/utils/validators'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'

type OutgoingMessage = {
  chatCode: string
  text: string
  images: Image[]
  tempId?: string
  sender: string
}

/**
 * WebSocket 状态管理 Store
 *
 * 核心职责：
 * - 管理 WebSocket 连接的生命周期（连接、断开、重连）
 * - 分发 WebSocket 事件到对应的业务 Store（Message, Room, Friend）
 * - 维护连接状态（Connecting, Open, Closed）并提供 UI 状态映射
 * - 处理 WebSocket 鉴权与 Token 刷新
 *
 * 调用路径：
 * - App 初始化：appStore.initializeApp -> websocketStore.initWS()
 * - 发送消息：messageStore.sendMessage -> websocketStore.sendData()
 * - 登出：userStore.logout -> websocketStore.close()
 *
 * 核心不变量：
 * - 连接断开时自动尝试重连（除非是主动关闭或鉴权失败）
 * - 始终使用最新的 AccessToken 进行连接
 *
 * 外部系统：
 * - useWebsocket (Composable) - 底层 WS 实现
 * - Backend WebSocket Endpoint
 */
export const useWebsocketStore = defineStore('websocket', () => {
  const { status, connect, send, close } = useWebsocket()
  
  // =========================================
  // 状态定义
  // =========================================
  
  /**
   * 初始化状态标记
   * 防止重复初始化
   */
  const isInitialized = ref(false)
  const { t } = i18n.global
  
  /**
   * 鉴权重试计数
   * 避免因 Token 问题导致的无限重连循环
   */
  const authRetryCount = ref(0)
  const maxAuthRetry = 1
  
  /**
   * 强制登出处理标记
   * 防止多次弹窗
   */
  const forceLogoutHandled = ref(false)

  /**
   * WebSocket UI 显示状态 (Computed)
   * 将底层 status 映射为 UI 可用的文本、颜色和样式类
   */
  const wsDisplayState = computed(() => {
    switch (status.value) {
      case 'OPEN':
        return { text: t('chat.ws_connected'), color: '#67C23A', class: 'status-online', type: 'success' }
      case 'CONNECTING':
        return { text: t('chat.ws_connecting'), color: '#E6A23C', class: 'status-connecting', type: 'warning' }
      case 'CLOSED':
        return { text: t('chat.ws_closed'), color: '#F56C6C', class: 'status-offline', type: 'danger' }
      default:
        return { text: t('chat.ws_unknown'), color: '#909399', class: 'status-offline', type: 'info' }
    }
  })

  // =========================================
  // 业务逻辑 / Actions
  // =========================================

  /**
   * 初始化 WebSocket 连接
   * 
   * 业务逻辑：
   * 1. 检查 Token 有效性，必要时自动刷新
   * 2. 构建带 Token 的 WebSocket URL
   * 3. 设置所有事件监听器（Message, Status, Ack, etc.）
   * 4. 建立连接
   * 
   * @param token Access Token
   */
  const initWS = async (token: string) => {
    if (!token || isInitialized.value) return
    authRetryCount.value = 0
    forceLogoutHandled.value = false

    const configStore = useConfigStore()
    const { websocketUrl } = storeToRefs(configStore)

    const roomStore = useRoomStore()
    const userStore = useUserStore()
    const friendStore = useFriendStore()

    let wsToken = token
    // 检查 Token 是否过期，过期则刷新
    if (isTokenExpired(wsToken)) {
      const refreshedToken = await userStore.refreshAccessToken()
      if (!refreshedToken) {
        close()
        await userStore.logout({ showDialog: true })
        return
      }
      wsToken = refreshedToken
    }

    const buildWsUrl = (nextToken: string) => {
      const url = new URL(websocketUrl.value)
      url.searchParams.set('token', nextToken)
      return url.toString()
    }
    const fullUrl = buildWsUrl(wsToken)

    // 处理强制登出（异地登录）
    const handleForceLogout = async () => {
      if (forceLogoutHandled.value) return
      forceLogoutHandled.value = true
      try {
        await showAlertDialog({
          message: t('auth.force_logout') || 'Account logged in elsewhere',
          type: 'warning',
        })
      } catch (e) {
        console.warn('[WARN] [WS] Force logout dialog closed unexpectedly', e)
      }
      await userStore.logout({ showDialog: false, silent: true })
    }

    connect(fullUrl, {
      // 收到消息 -> messageStore 处理
      onMessage: (data: unknown) => {
        const messageStore = useMessageStore()
        messageStore.receiveMessage(data).then(() => { })
      },
      // 用户状态变更 -> roomStore 更新
      onUserStatus: (uid: string, isOnline: boolean) => {
        roomStore.updateMemberStatus(uid, isOnline)
      },
      // 成员加入 -> roomStore 更新 & 预加载头像
      onChatMemberAdd: async (member: ChatMember) => {
        if (member.avatar && (member.avatar.imageName || member.avatar.imageThumbUrl || member.avatar.imageUrl)) {
          const imageStore = useImageStore()
          try {
            await imageStore.ensureThumbBlobUrl(member.avatar)
          } catch (e) {
            console.error('[WebSocket] Avatar pre-fetch failed', e)
          }
        }
        roomStore.addRoomMember(member)
      },
      // 成员离开 -> roomStore 更新
      onChatMemberLeave: (payload) => {
        roomStore.handleMemberLeave(payload)
      },
      // 成员被移除 -> roomStore 更新
      onChatMemberRemoved: (payload) => {
        roomStore.handleMemberRemoved(payload).then(() => { })
      },
      // 房主转让 -> roomStore 更新
      onChatOwnerTransfer: (payload) => {
        roomStore.handleOwnerTransfer(payload)
      },
      // 房间解散 -> roomStore 更新
      onChatRoomDisband: (payload) => {
        roomStore.handleRoomDisband(payload).then(() => { })
      },
      // 强制登出
      onForceLogout: async () => {
        await handleForceLogout()
      },
      // 消息 ACK -> messageStore 更新状态
      onAck: (data: AckPayload) => {
        const messageStore = useMessageStore()
        if (data && data.tempId && data.seqId) {
          messageStore.handleAck(data.tempId, data.seqId)
        } else {
          console.warn('[WebSocket] Invalid ACK format:', data)
        }
      },
      // 好友申请通知 -> friendStore 刷新 & 弹窗
      onFriendRequest: (payload: FriendRequest) => {
        friendStore.fetchRequests()
        
        ElNotification({
          title: t('friend.new_requests'),
          message: payload.senderNickname || 'Unknown',
          type: 'info',
          duration: 5000,
          position: 'top-right'
        })
      },
      // 心跳包载荷
      getHeartbeatPayload: () => {
        return 'PING' + roomStore.currentRoomCode
      },
      // 连接关闭处理（重连逻辑）
      onClose: async (event: CloseEvent) => {
        isInitialized.value = false
        // 4003: 强制登出
        if (event.code === 4003) {
          await handleForceLogout()
          return
        }
        // 4001/4002: Token 无效/过期 -> 尝试刷新并重连
        if (event.code === 4001 || event.code === 4002) {
          if (authRetryCount.value < maxAuthRetry) {
            authRetryCount.value += 1
            const refreshedToken = await userStore.refreshAccessToken()
            if (refreshedToken) {
              await initWS(refreshedToken)
              return
            }
          }
          close()
          await userStore.logout({ showDialog: true })
          return
        }
      },
      // 重连成功 -> 同步离线消息
      onReconnect: async () => {
        console.log('[WebSocket] Reconnected, syncing messages...')
        const messageStore = useMessageStore()
        await messageStore.syncAfterReconnect()
      },
      // 获取重连 URL (刷新 Token)
      getReconnectUrl: async () => {
        const refreshedToken = await userStore.refreshAccessToken()
        if (!refreshedToken) {
          await userStore.logout({ showDialog: true })
          return null
        }
        return buildWsUrl(refreshedToken)
      }
    })

    isInitialized.value = true
  }

  /**
   * 发送 WebSocket 数据
   * 
   * @param data 发送的消息载荷
   */
  const sendData = (data: OutgoingMessage) => {
    try {
      send(data)
    } catch (e) {
      console.error('[ERROR] [WS] Send Message Error:', e)
      ElMessage.error(t('chat.ws_send_error'))
    }
  }

  /**
   * 重置 Store 状态
   * 关闭连接并重置初始化标记
   */
  const resetState = () => {
    close()
    isInitialized.value = false
  }

  return {
    status,
    wsDisplayState,
    isInitialized,
    initWS,
    sendData,
    close,
    resetState,
  }
})
