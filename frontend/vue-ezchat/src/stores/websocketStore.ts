import { defineStore, storeToRefs } from 'pinia'
import { computed, ref, watch } from 'vue'
import { useWebsocket } from '@/WS/useWebsocket.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import { ElMessage } from 'element-plus'
import type { Image, Message, ChatMember } from '@/type'
import { useConfigStore } from '@/stores/configStore.ts'
import { useImageStore } from '@/stores/imageStore.ts'
import i18n from '@/i18n'
import { isTokenExpired } from '@/utils/validators'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'

/**
 * WebsocketStore：管理 WebSocket 连接与分发
 *
 * 设计原则：
 * - WS 连接属于“应用级单例”，避免多页面/多组件重复建连
 * - 业务消息分发给 RoomStore / MessageStore / UserStore
 * - 主动断开（logout）时不应该触发重连（由底层 useWebsocket 控制）
 */
type OutgoingMessage = {
  chatCode: string
  text: string
  images: Image[]
  tempId?: string
  sender: string
}

export const useWebsocketStore = defineStore('websocket', () => {
  // status 已在 useWebsocket 内做“CONNECTING 最短展示 0.5s”的平滑处理
  const { status, connect, send, close } = useWebsocket()
  const isInitialized = ref(false)
  const { t } = i18n.global
  const authRetryCount = ref(0)
  const maxAuthRetry = 1
  const forceLogoutHandled = ref(false)

  /**
   * 给 UI 展示用的连接状态（含颜色/徽标类型）
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

  /**
   * 初始化 WebSocket 连接
   *
   * @param token JWT token（拼接到 `/websocket/{token}`）
   */
  const initWS = async (token: string) => {
    if (!token || isInitialized.value) return
    authRetryCount.value = 0
    forceLogoutHandled.value = false

    const configStore = useConfigStore()
    const { websocketUrl } = storeToRefs(configStore)

    const roomStore = useRoomStore()
    const userStore = useUserStore()

    let wsToken = token
    if (isTokenExpired(wsToken)) {
      const refreshedToken = await userStore.refreshAccessToken()
      if (!refreshedToken) {
        close()
        await userStore.logout({ showDialog: true })
        return
      }
      wsToken = refreshedToken
    }

    // 拼接 Token 到 URL
    const buildWsUrl = (nextToken: string) => {
      const url = new URL(websocketUrl.value)
      url.searchParams.set('token', nextToken)
      return url.toString()
    }
    const fullUrl = buildWsUrl(wsToken)

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
      onMessage: (data: any) => {
        // 业务消息：聊天消息（MESSAGE）
        // data 已经是 Message 类型 (包含 text, image, mixed, member_join 等)
        // [FIX] 接受 loose data，并在业务层（store）进行类型断言，避免传输层因 strict check 丢包
        const messageStore = useMessageStore()
        messageStore.receiveMessage(data).then(() => { })
      },
      onUserStatus: (uid: string, isOnline: boolean) => {
        // 系统消息：在线状态变更（USER_STATUS）
        roomStore.updateMemberStatus(uid, isOnline)
      },
      onChatMemberAdd: async (member: ChatMember) => {
        // [Optimization] Wait for Avatar (Thumb) to be ready before updating UI list
        // This avoids layout shift or "blank avatar" flash
        if (member.avatar && (member.avatar.imageName || member.avatar.imageThumbUrl || member.avatar.imageUrl)) {
          const imageStore = useImageStore()
          try {
            // Note: ensureThumbBlobUrl handles fetching & caching logic
            await imageStore.ensureThumbBlobUrl(member.avatar)
          } catch (e) {
            console.error('[WebSocket] Avatar pre-fetch failed', e)
          }
        }
        roomStore.addRoomMember(member)
      },
      onChatMemberLeave: (payload) => {
        roomStore.handleMemberLeave(payload)
      },
      onChatMemberRemoved: (payload) => {
        roomStore.handleMemberRemoved(payload).then(() => { })
      },
      onChatOwnerTransfer: (payload) => {
        roomStore.handleOwnerTransfer(payload)
      },
      onChatRoomDisband: (payload) => {
        roomStore.handleRoomDisband(payload).then(() => { })
      },
      onForceLogout: async () => {
        await handleForceLogout()
      },
      onAck: (data: any) => {
        // 系统消息：服务端 ACK，标记本地“发送中”消息为 sent
        // data 结构: { tempId: string, seqId: number }
        const messageStore = useMessageStore()
        if (data && data.tempId && data.seqId) {
          messageStore.handleAck(data.tempId, data.seqId)
        } else {
          // 兼容旧格式或是出错
          console.warn('[WebSocket] Invalid ACK format:', data)
        }
      },
      getHeartbeatPayload: () => {
        // 心跳携带 chatCode：让后端在断线时能记录 lastSeenAt
        return 'PING' + roomStore.currentRoomCode
      },
      onClose: async (event: CloseEvent) => {
        isInitialized.value = false
        if (event.code === 4003) {
          await handleForceLogout()
          return
        }
        // 认证失败/过期：不再重连，直接退出登录并回到首页
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
      onReconnect: async () => {
        // 重连成功后同步当前房间的消息
        console.log('[WebSocket] Reconnected, syncing messages...')
        const messageStore = useMessageStore()
        await messageStore.syncAfterReconnect()
      },
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

  // 仅暴露底层的 send 方法，业务逻辑移交 messageStore
  /**
   * 发送 WS 消息（业务层应统一通过 messageStore 调用）
   *
   * @param data 发往服务端的消息载荷
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
   * 重置 Store 业务数据（不重置 UI 状态）
   *
   * 业务场景：App 初始化/账号切换前，主动断开 WS 并清理连接相关数据，防止旧连接残留导致“串房间/串账号”。
   *
   * 注意：
   * - 这里的目标是“断开旧连接”
   * - 不应重置 wsDisplayState 等纯 UI 展示状态（由 status 的 watch 自然驱动）
   */
  const resetState = () => {
    // 主动关闭底层连接（useWebsocket 会标记为主动关闭，不触发重连）
    close()
    // 重置初始化标记
    isInitialized.value = false
  }

  return {
    status,
    wsDisplayState,
    isInitialized,
    initWS,
    sendData, // 导出供 messageStore 使用
    close,
    resetState,
  }
})
