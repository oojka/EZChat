import {defineStore, storeToRefs} from 'pinia'
import {computed, ref, watch} from 'vue'
import {useWebsocket} from '@/WS/useWebsocket.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {useMessageStore} from '@/stores/messageStore.ts'
import {useRoomStore} from '@/stores/roomStore.ts'
import {ElMessage} from 'element-plus'
import router from '@/router'
import type {Message} from '@/type'
import {useConfigStore} from '@/stores/configStore.ts'
import i18n from '@/i18n'

export const useWebsocketStore = defineStore('websocket', () => {
  const { status, connect, send, close } = useWebsocket()
  const isInitialized = ref(false)
  const { t } = i18n.global

  // --- 状态平滑切换逻辑 ---
  const delayedStatus = ref(status.value)
  let statusTimer: ReturnType<typeof setTimeout> | null = null

  watch(status, (newStatus) => {
    if (statusTimer) clearTimeout(statusTimer)
    statusTimer = setTimeout(() => {
      delayedStatus.value = newStatus
    }, 500)
  })

  const wsDisplayState = computed(() => {
    switch (delayedStatus.value) {
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

  const initWS = (token: string) => {
    if (!token || isInitialized.value) return

    const configStore = useConfigStore()
    const { websocketUrl } = storeToRefs(configStore)

    const roomStore = useRoomStore()
    const userStore = useUserStore()

    // 拼接 Token 到 URL
    const fullUrl = `${websocketUrl.value}/${token}`

    connect(fullUrl, {
      onMessage: (data: any) => {
        if (typeof data === 'string') return
        const messageStore = useMessageStore()
        messageStore.receiveMessage(data as Message).then(() => {})
      },
      onUserStatus: (uid: string, isOnline: boolean) => {
        roomStore.updateMemberStatus(uid, isOnline)
      },
      onAck: (tempId: string) => {
        const messageStore = useMessageStore()
        messageStore.handleAck(tempId)
      },
      getHeartbeatPayload: () => {
        return 'PING' + roomStore.currentRoomCode
      },
      onClose: async (event: CloseEvent) => {
        isInitialized.value = false
        if (event.code === 4001 || event.code === 4002) {
          close()
          ElMessage.error(t('auth.session_expired'))
          userStore.logout()
          return router.replace('/')
        }
      },
    })

    isInitialized.value = true
  }

  // 仅暴露底层的 send 方法，业务逻辑移交 messageStore
  const sendData = (data: any) => {
    try {
      send(data)
    } catch (e) {
      console.error('[ERROR] [WS] Send Message Error:', e)
      ElMessage.error(t('chat.ws_send_error'))
    }
  }

  return {
    status,
    wsDisplayState,
    isInitialized,
    initWS,
    sendData, // 导出供 messageStore 使用
    close,
  }
})
