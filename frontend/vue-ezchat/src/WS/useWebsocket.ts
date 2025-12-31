import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import type {Message, UserStatus, WebSocketResult} from '@/type'

// 定义 Connect 方法需要的参数接口
interface ConnectOptions {
  onMessage: (data: Message) => void
  onUserStatus: (uid: string, isOnline: boolean) => void
  onAck: (tempId: string) => void
  getHeartbeatPayload: () => string
  // 新增：关闭事件回调，用于处理 4001 等特殊状态码
  onClose?: (event: CloseEvent) => void
}

const isMessagePayload = (data: unknown): data is Message => {
  if (!data || typeof data !== 'object') return false
  const payload = data as Record<string, unknown>
  return (
    typeof payload.sender === 'string'
    && typeof payload.chatCode === 'string'
    && typeof payload.createTime === 'string'
  )
}

export function useWebsocket() {
  const socket = ref<WebSocket | null>(null)
  const lockReconnect = ref(false)
  const status = ref<'CONNECTING' | 'OPEN' | 'CLOSED'>('CLOSED')

  let currentUrl: string = ''
  let currentOptions: ConnectOptions | null = null
  let heartbeatTimer: number | null = null
  // 新增：标记是否为主动关闭（主动退出时不应重连）
  let isIntentionalClose = false

  // --- 核心方法 ---

  const connect = (url: string, options: ConnectOptions) => {
    currentUrl = url
    currentOptions = options
    isIntentionalClose = false // 重置主动关闭标记

    if (socket.value?.readyState === WebSocket.OPEN) return

    try {
      socket.value = new WebSocket(url)
      status.value = 'CONNECTING'
    } catch (e) {
      console.error('[ERROR] [WS] WebSocket creation failed', e)
      reconnect()
      return
    }

    // --- 事件监听 ---

    socket.value.onopen = () => {
      status.value = 'OPEN'
      startHeartbeat()
    }

    socket.value.onmessage = (event) => {
      if (event.data === 'PONG') return

      try {
        const result: WebSocketResult = JSON.parse(event.data)
        const parseData = (data: unknown) => (typeof data === 'string' ? JSON.parse(data) : data)

        if (result.isSystemMessage) {
          if (result.type === 'ACK') {
            currentOptions?.onAck(result.data as string)
          } else if (result.type === 'USER_STATUS') {
            const userStatus: UserStatus = parseData(result.data)
            currentOptions?.onUserStatus(userStatus.uid, userStatus.online)
          }
        } else if (result.type === 'MESSAGE') {
          const msg = parseData(result.data)
          if (isMessagePayload(msg)) {
            currentOptions?.onMessage(msg)
          } else {
            console.warn('[WARN] [WS] Received message with invalid shape, ignored.')
          }
        }
      } catch (e) {
        // 生产环境建议减少此类报错弹窗，避免刷屏，改用 console.error
        console.error('[ERROR] [WS] WebSocket Message Parse Error', e)
      }
    }

    socket.value.onclose = (event) => {
      status.value = 'CLOSED'
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
      ElMessage.warning('Websocket接続が切れました。再接続中...')
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
    if (socket.value) {
      socket.value.close()
    }
  }

  return {
    status,
    connect,
    send,
    close, // 导出
  }
}
