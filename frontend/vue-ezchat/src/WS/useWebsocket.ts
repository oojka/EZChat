import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Message, UserStatus, WebSocketResult } from '@/type'
import i18n from '@/i18n'

const { t } = i18n.global

// 定义 Connect 方法需要的参数类型
type ConnectOptions = {
  onMessage: (data: any) => void
  onUserStatus: (uid: string, isOnline: boolean) => void
  onAck: (tempId: string) => void
  getHeartbeatPayload: () => string
  // 新增：关闭事件回调，用于处理 4001 等特殊状态码
  onClose?: (event: CloseEvent) => void
  onChatMemberAdd?: (member: any) => void
}

// 修改：宽松的 Payload 检查，只确保是对象，具体字段校验交给业务层
const isMessagePayload = (data: unknown): boolean => {
  return !!data && typeof data === 'object'
}

export function useWebsocket() {
  const socket = ref<WebSocket | null>(null)
  const lockReconnect = ref(false)
  // rawStatus：真实连接状态（事件驱动）
  const rawStatus = ref<'CONNECTING' | 'OPEN' | 'CLOSED'>('CLOSED')
  // status：给 UI 用的“平滑状态”，避免指示灯抖动
  const status = ref<'CONNECTING' | 'OPEN' | 'CLOSED'>(rawStatus.value)

  let currentUrl: string = ''
  let currentOptions: ConnectOptions | null = null
  let heartbeatTimer: number | null = null
  // 新增：标记是否为主动关闭（主动退出时不应重连）
  let isIntentionalClose = false
  // 状态平滑：CONNECTING（黄灯）出现后，0.5s 内不允许再变化，给用户“正在连接”的稳定感
  const MIN_CONNECTING_MS = 500
  let connectingStartAt = 0
  let statusDelayTimer: number | null = null

  const cleanupStatusDelay = () => {
    if (statusDelayTimer) {
      clearTimeout(statusDelayTimer)
      statusDelayTimer = null
    }
  }

  /**
   * 设置状态：
   * - rawStatus：实时更新
   * - status（UI）：CONNECTING 立即更新；从 CONNECTING 切到 OPEN/CLOSED 时最少停留 0.5s
   */
  const setStatus = (next: 'CONNECTING' | 'OPEN' | 'CLOSED') => {
    rawStatus.value = next

    if (next === 'CONNECTING') {
      cleanupStatusDelay()
      connectingStartAt = Date.now()
      status.value = 'CONNECTING'
      return
    }

    // 如果当前 UI 正在显示 CONNECTING，则至少展示 MIN_CONNECTING_MS，避免“黄一下就变绿/红”
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

  // --- 核心方法 ---

  const connect = (url: string, options: ConnectOptions) => {
    currentUrl = url
    currentOptions = options
    isIntentionalClose = false // 重置主动关闭标记

    if (socket.value?.readyState === WebSocket.OPEN) return

    try {
      socket.value = new WebSocket(url)
      setStatus('CONNECTING')
    } catch (e) {
      console.error('[ERROR] [WS] WebSocket creation failed', e)
      reconnect()
      return
    }

    // --- 事件监听 ---

    socket.value.onopen = () => {
      setStatus('OPEN')
      startHeartbeat()
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
        // 解析 payload
        const payload = parseData(result.data)

        // 优先使用 Status Code (新协议)
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
              // ACK 的 data 通常是 tempId 字符串
              const tempId = typeof result.data === 'string' ? result.data : String(result.data)
              currentOptions?.onAck(tempId)
              break
            case 3001: // MEMBER_JOIN (System Broadcast)
              // payload 是 JoinBroadcastVO，包含 { member, text, type=11, ... }
              // 1. 独立处理成员列表更新
              console.log('Member Join:', payload)
              if (payload.member) {
                console.log('Member Join:', payload.member)
                currentOptions?.onChatMemberAdd?.(payload.member)
              }
              // 2. 同时作为普通消息分发，用于在 UI 显示系统提示（如 "XXX joined"）
              // 此时 payload 被视为 MemberJoinMessage (Type 11)，TS 校验通过（因为 MemberJoinMessage 只有 text）
              if (isMessagePayload(payload)) {
                currentOptions?.onMessage(payload)
              }
              break
          }
          return
        }

        // [Legacy Support] 旧协议兼容 (isSystemMessage / type)
        // ... (保留部分逻辑用于稳健性，但移除导致类型报错的 msg.member 访问)
        if (result.isSystemMessage) {
          if (result.type === 'ACK') {
            if (typeof result.data === 'string') currentOptions?.onAck(result.data)
          } else if (result.type === 'USER_STATUS') {
            const userStatus = parseData(result.data) as UserStatus
            currentOptions?.onUserStatus(userStatus.uid, userStatus.online)
          }
        } else if (result.type === 'MESSAGE') {
          const msg = parseData(result.data)
          if (isMessagePayload(msg)) {
            currentOptions?.onMessage(msg)
            // [REMOVED] 移除 type===11 的特殊处理，改用 code 3001
          }
        }
      } catch (e) {
        console.error('[ERROR] [WS] WebSocket Message Parse Error', e)
      }
    }

    socket.value.onclose = (event) => {
      setStatus('CLOSED')
      cleanupHeartbeat()

      // 1. 优先触发回调，让业务层有机会拦截（例如处理 4001）
      if (currentOptions?.onClose) {
        currentOptions.onClose(event)
      }

      // 2. 如果是 4001(过期)/4002(认证失败) 或 用户主动断开，则不重连
      if (event.code === 4001 || event.code === 4002 || isIntentionalClose) {
        return
      }

      // 3. 其他情况（网络波动等）触发重连
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

    // 设置重连延迟
    setTimeout(() => {
      if (currentUrl && currentOptions) {
        // 确保不是在“不再重连”的状态下
        if (!isIntentionalClose) {
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

  // 新增：暴露关闭方法，供登出使用
  const close = () => {
    isIntentionalClose = true
    // 关键修复：
    // - close() 是异步的，如果此时 socket 仍处于 OPEN，下一次 connect() 可能会被“readyState === OPEN”挡住
    // - 因此这里先清空引用并立即置为 CLOSED，确保后续可以立即重建连接
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
    close, // 导出
  }
}
