import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { 
  getFriendListApi, 
  getPendingRequestsApi, 
  sendFriendRequestApi, 
  handleFriendRequestApi, 
  removeFriendApi,
  getOrCreatePrivateChatApi
} from '@/api/friend'
import type { Friend, FriendRequest } from '@/type'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import i18n from '@/i18n'

export const useFriendStore = defineStore('friend', () => {
  const router = useRouter()
  const { t } = i18n.global
  
  const friends = ref<Friend[]>([])
  const requests = ref<FriendRequest[]>([])
  const loading = ref(false)

  // Pending count for badges
  const pendingCount = computed(() => requests.value.filter(r => r.status === 0).length)

  const fetchFriends = async () => {
    loading.value = true
    try {
      const res = await getFriendListApi()
      if (res.code === 200) {
        friends.value = res.data
      }
    } finally {
      loading.value = false
    }
  }

  const fetchRequests = async () => {
    try {
      const res = await getPendingRequestsApi()
      if (res.code === 200) {
        requests.value = res.data
      }
    } catch (e) {
      console.error(e)
    }
  }

  const sendRequest = async (targetUid: string) => {
    const res = await sendFriendRequestApi({ targetUid })
    if (res.code === 200) {
      ElMessage.success(t('friend.request_sent'))
    } else {
      ElMessage.error(res.message || t('friend.request_failed'))
    }
  }

  const handleRequest = async (requestId: number, accept: boolean) => {
    const res = await handleFriendRequestApi({ requestId, accept })
    if (res.code === 200) {
      ElMessage.success(accept ? t('friend.accepted') : t('friend.rejected'))
      // Remove from list or refresh
      await fetchRequests()
      if (accept) await fetchFriends()
    } else {
      ElMessage.error(res.message || t('friend.operation_failed'))
    }
  }

  const removeFriend = async (friendUid: string) => {
    const res = await removeFriendApi(friendUid)
    if (res.code === 200) {
      ElMessage.success(t('friend.friend_removed'))
      await fetchFriends()
    } else {
      ElMessage.error(res.message || t('friend.remove_failed'))
    }
  }

  const startChat = async (friendUid: string) => {
    try {
      const res = await getOrCreatePrivateChatApi({ targetUid: friendUid })
      if (res.code === 200 && res.data) {
        const chatCode = res.data
        // Navigate to chat
        router.push(`/chat/${chatCode}`)
      } else {
        ElMessage.error(res.message || t('friend.start_chat_failed'))
      }
    } catch (e) {
      ElMessage.error(t('dialog.network_error'))
    }
  }

  return {
    friends,
    requests,
    loading,
    pendingCount,
    fetchFriends,
    fetchRequests,
    sendRequest,
    handleRequest,
    removeFriend,
    startChat
  }
})
