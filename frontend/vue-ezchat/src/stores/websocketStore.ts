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

export const useWebsocketStore = defineStore('websocket', () => {
  const { status, connect, send, close } = useWebsocket()
  const isInitialized = ref(false)
  const { t } = i18n.global
  const authRetryCount = ref(0)
  const maxAuthRetry = 1
  const forceLogoutHandled = ref(false)

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
      onMessage: (data: unknown) => {
        const messageStore = useMessageStore()
        messageStore.receiveMessage(data).then(() => { })
      },
      onUserStatus: (uid: string, isOnline: boolean) => {
        roomStore.updateMemberStatus(uid, isOnline)
      },
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
      onAck: (data: AckPayload) => {
        const messageStore = useMessageStore()
        if (data && data.tempId && data.seqId) {
          messageStore.handleAck(data.tempId, data.seqId)
        } else {
          console.warn('[WebSocket] Invalid ACK format:', data)
        }
      },
      onFriendRequest: (payload: FriendRequest) => {
        // 1. Refresh request list
        friendStore.fetchRequests()
        
        // 2. Show notification
        ElNotification({
          title: t('friend.new_requests'),
          message: payload.senderNickname || 'Unknown',
          type: 'info',
          duration: 5000,
          position: 'top-right'
        })
      },
      getHeartbeatPayload: () => {
        return 'PING' + roomStore.currentRoomCode
      },
      onClose: async (event: CloseEvent) => {
        isInitialized.value = false
        if (event.code === 4003) {
          await handleForceLogout()
          return
        }
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

  const sendData = (data: OutgoingMessage) => {
    try {
      send(data)
    } catch (e) {
      console.error('[ERROR] [WS] Send Message Error:', e)
      ElMessage.error(t('chat.ws_send_error'))
    }
  }

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
