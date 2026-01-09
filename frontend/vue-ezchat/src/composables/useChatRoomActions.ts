import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { showConfirmDialog } from '@/components/dialogs/confirmDialog'
import { disbandChatApi, leaveChatApi } from '@/api/Chat'

export const useChatRoomActions = () => {
  const router = useRouter()
  const roomStore = useRoomStore()
  const userStore = useUserStore()
  const { currentRoom } = storeToRefs(roomStore)
  const { loginUserInfo } = storeToRefs(userStore)

  const isOwner = computed(() => {
    const room = currentRoom.value
    const user = loginUserInfo.value
    return Boolean(room && user && room.ownerUid === user.uid)
  })

  const canLeave = computed(() => {
    const room = currentRoom.value
    if (!room) return false
    if (!isOwner.value) return true
    return (room.memberCount || 0) > 1
  })

  const canDisband = computed(() => {
    return isOwner.value
  })

  const executeLeave = async () => {
    const room = currentRoom.value
    if (!room) return

    const res = await leaveChatApi(room.chatCode)
    if (res?.status === 1) {
      await roomStore.initRoomList()
      await router.push('/chat')
    }
  }

  const executeDisband = async () => {
    const room = currentRoom.value
    if (!room) return

    const res = await disbandChatApi(room.chatCode)
    if (res?.status === 1) {
      await roomStore.initRoomList()
      await router.push('/chat')
    }
  }

  const confirmLeave = () => {
    const room = currentRoom.value
    if (!room || !canLeave.value) return

    showConfirmDialog({
      title: 'dialog.confirm',
      message: isOwner.value ? 'chat.leave_owner_confirm' : 'chat.leave_confirm',
      confirmText: 'common.confirm',
      cancelText: 'common.cancel',
      type: 'warning',
      onConfirm: () => {
        executeLeave().then(() => { })
      }
    })
  }

  const confirmDisband = () => {
    const room = currentRoom.value
    if (!room || !canDisband.value) return

    showConfirmDialog({
      title: 'dialog.confirm',
      message: 'chat.disband_confirm',
      confirmText: 'common.confirm',
      cancelText: 'common.cancel',
      type: 'danger',
      onConfirm: () => {
        executeDisband().then(() => { })
      }
    })
  }

  return {
    isOwner,
    canLeave,
    canDisband,
    confirmLeave,
    confirmDisband,
  }
}
