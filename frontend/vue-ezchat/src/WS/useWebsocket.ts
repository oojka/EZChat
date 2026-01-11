import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Message, UserStatus, WebSocketResult, AckPayload, MemberLeaveBroadcastPayload, MemberRemovedBroadcastPayload, OwnerTransferBroadcastPayload, RoomDisbandBroadcastPayload, ForceLogoutPayload, FriendRequest } from '@/type'
import { isAckPayload, isMemberLeavePayload, isMemberRemovedPayload, isOwnerTransferPayload, isRoomDisbandPayload, isForceLogoutPayload } from '@/utils/validators'
import i18n from '@/i18n'

const { t } = i18n.global

// 定义 Connect 方法需要的参数类型
type ConnectOptions = {
  onMessage: (data: any) => void
  onUserStatus: (uid: string, isOnline: boolean) => void
  onAck: (data: AckPayload) => void
  getHeartbeatPayload: () => string
  getReconnectUrl?: () => Promise<string | null>
  onClose?: (event: CloseEvent) => void
  onChatMemberAdd?: (member: any) => void
  onChatMemberLeave?: (payload: MemberLeaveBroadcastPayload) => void
  onChatMemberRemoved?: (payload: MemberRemovedBroadcastPayload) => void
  onChatOwnerTransfer?: (payload: OwnerTransferBroadcastPayload) => void
  onChatRoomDisband?: (payload: RoomDisbandBroadcastPayload) => void
  onForceLogout?: (payload: ForceLogoutPayload) => void
  onFriendRequest?: (payload: FriendRequest) => void
  onReconnect?: () => void
}

const isMessagePayload = (data: unknown): boolean => {
  return !!data && typeof data === 'object'
}

export function useWebsocket() {
  const socket = ref<WebSocket | null>(null)
  const lockReconnect = ref(false)
  const rawStatus = ref<'CONNECTING' | 'OPEN' | 'CLOSED'>('CLOSED')
  const status = ref<'CONNECTING' | 'OPEN' | 'CLOSED'>(rawStatus.value)

  let currentUrl: string = ''
  let currentOptions: ConnectOptions | null = null
  let heartbeatTimer: number | null = null
  let isIntentionalClose = false
  let wasConnected = false
  const MIN_CONNECTING_MS = 500
  let connectingStartAt = 0
  let statusDelayTimer: number | null = null

  const cleanupStatusDelay = () => {
    if (statusDelayTimer) {
      clearTimeout(statusDelayTimer)
      statusDelayTimer = null
    }
  }

  const setStatus = (next: 'CONNECTING' | 'OPEN' | 'CLOSED') => {
    rawStatus.value = next

    if (next === 'CONNECTING') {
      cleanupStatusDelay()
      connectingStartAt = Date.now()
      status.value = 'CONNECTING'
      return
    }

    if (status.value === 'CONNECTING') {
      const elapsed = Date.now() - connectingStartAt
      const remain = Math.max(0, MIN_CONNECTING_MS - elapsed)
      if (remain > 0) {
        cleanupStatusDelay()
        statusDelayTimer = window.setTimeout(() => {
          status.value = next
          statusDelayTimer = null
        }, remain)
        return
      }
    }

    cleanupStatusDelay()
    status.value = next
  }

  const connect = (url: string, options: ConnectOptions) => {
    currentUrl = url
    currentOptions = options
    isIntentionalClose = false

    if (socket.value?.readyState === WebSocket.OPEN) return

    try {
      socket.value = new WebSocket(url)
      setStatus('CONNECTING')
    } catch (e) {
      console.error('[ERROR] [WS] WebSocket creation failed', e)
      reconnect()
      return
    }

    socket.value.onopen = () => {
      const isReconnect = wasConnected
      wasConnected = true
      setStatus('OPEN')
      startHeartbeat()

      if (isReconnect && currentOptions?.onReconnect) {
        currentOptions.onReconnect()
      }
    }

    socket.value.onmessage = (event) => {
      if (event.data === 'PONG') return

      try {
        const result: WebSocketResult = JSON.parse(event.data)
        const parseData = (data: unknown) => {
          if (typeof data !== 'string') return data
          try {
            return JSON.parse(data)
          } catch (e) {
            return data
          }
        }
        const payload = parseData(result.data)

        if (result.code) {
          switch (result.code) {
            case 1001: // MESSAGE
              if (isMessagePayload(payload)) currentOptions?.onMessage(payload)
              break
            case 2001: // USER_STATUS
              const us = payload as UserStatus
              currentOptions?.onUserStatus(us.uid, us.online)
              break
            case 2002: // ACK
              if (isAckPayload(payload)) {
                currentOptions?.onAck(payload)
              } else {
                console.warn('[WS] Invalid ACK payload:', payload)
              }
              break
            case 3001: // MEMBER_JOIN
              if (payload.member) {
                currentOptions?.onChatMemberAdd?.(payload.member)
              }
              if (isMessagePayload(payload)) {
                currentOptions?.onMessage(payload)
              }
              break
            case 3002: // MEMBER_LEAVE
              if (isMemberLeavePayload(payload)) {
                currentOptions?.onChatMemberLeave?.(payload)
              } else {
                console.warn('[WS] Invalid MEMBER_LEAVE payload:', payload)
              }
              break
            case 3003: // OWNER_TRANSFER
              if (isOwnerTransferPayload(payload)) {
                currentOptions?.onChatOwnerTransfer?.(payload)
              } else {
                console.warn('[WS] Invalid OWNER_TRANSFER payload:', payload)
              }
              break
            case 3004: // ROOM_DISBAND
              if (isRoomDisbandPayload(payload)) {
                currentOptions?.onChatRoomDisband?.(payload)
              } else {
                console.warn('[WS] Invalid ROOM_DISBAND payload:', payload)
              }
              break
            case 3005: // MEMBER_REMOVED
              if (isMemberRemovedPayload(payload)) {
                currentOptions?.onChatMemberRemoved?.(payload)
              } else {
                console.warn('[WS] Invalid MEMBER_REMOVED payload:', payload)
              }
              break
            case 2003: // FORCE_LOGOUT
              if (isForceLogoutPayload(payload)) {
                currentOptions?.onForceLogout?.(payload)
              } else {
                console.warn('[WS] Invalid FORCE_LOGOUT payload:', payload)
              }
              break
            case 5001: // FRIEND_REQUEST
              currentOptions?.onFriendRequest?.(payload as FriendRequest)
              break
          }
          return
        }

        // Legacy Support
        if (result.isSystemMessage) {
          if (result.type === 'ACK') {
            const ackData = parseData(result.data)
            if (isAckPayload(ackData)) {
              currentOptions?.onAck(ackData)
            }
          } else if (result.type === 'USER_STATUS') {
            const userStatus = parseData(result.data) as UserStatus
            currentOptions?.onUserStatus(userStatus.uid, userStatus.online)
          }
        } else if (result.type === 'MESSAGE') {
          const msg = parseData(result.data)
          if (isMessagePayload(msg)) {
            currentOptions?.onMessage(msg)
          }
        }
      } catch (e) {
        console.error('[ERROR] [WS] WebSocket Message Parse Error', e)
      }
    }

    socket.value.onclose = (event) => {
      setStatus('CLOSED')
      cleanupHeartbeat()

      if (currentOptions?.onClose) {
        currentOptions.onClose(event)
      }

      if (event.code === 4001 || event.code === 4002 || event.code === 4003 || isIntentionalClose) {
        return
      }

      ElMessage.warning(t('chat.ws_reconnecting'))
      console.warn('[WARN] [WS] WebSocket Closed', event)
      reconnect()
    }

    socket.value.onerror = (error) => {
      console.error('[ERROR] [WS] WebSocket Error', error)
    }
  }

  const reconnect = () => {
    if (lockReconnect.value) return
    lockReconnect.value = true

    setTimeout(async () => {
      if (currentUrl && currentOptions) {
        if (!isIntentionalClose) {
          if (currentOptions.getReconnectUrl) {
            const nextUrl = await currentOptions.getReconnectUrl()
            if (!nextUrl) {
              lockReconnect.value = false
              return
            }
            currentUrl = nextUrl
          }
          connect(currentUrl, currentOptions)
        }
      }
      lockReconnect.value = false
    }, 5000)
  }

  const send = (data: string | Record<string, unknown>) => {
    if (socket.value?.readyState === WebSocket.OPEN) {
      const payload = typeof data === 'object' ? JSON.stringify(data) : data
      socket.value.send(payload)
    } else {
      console.warn('[WARN] [WS] WebSocket is not open. State:', socket.value?.readyState)
    }
  }

  const startHeartbeat = () => {
    cleanupHeartbeat()
    heartbeatTimer = window.setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        const payload = currentOptions?.getHeartbeatPayload
          ? currentOptions.getHeartbeatPayload()
          : 'PING'
        socket.value.send(payload)
      }
    }, 30000)
  }

  const cleanupHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const close = () => {
    isIntentionalClose = true
    const ws = socket.value
    socket.value = null
    setStatus('CLOSED')
    cleanupHeartbeat()
    if (ws) {
      try {
        ws.close()
      } catch (e) {
        console.warn('[WARN] [WS] WebSocket close failed', e)
      }
    }
  }

  return {
    rawStatus,
    status,
    connect,
    send,
    close,
  }
}
