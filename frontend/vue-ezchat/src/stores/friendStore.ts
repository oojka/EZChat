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

/**
 * 好友状态管理 Store
 *
 * 核心职责：
 * - 管理好友列表与好友申请列表
 * - 处理好友相关的交互（添加、删除、接受/拒绝）
 * - 维护好友申请的未读计数（Pending Count）
 *
 * 调用路径：
 * - 好友列表页：useFriendStore.fetchFriends()
 * - 好友申请页：useFriendStore.fetchRequests()
 * - 发起聊天：useFriendStore.startChat()
 *
 * 核心不变量：
 * - 好友列表与申请列表应与后端保持同步
 * - pendingCount 实时反映待处理申请的数量
 *
 * 外部系统：
 * - FriendAPI - 好友相关接口
 * - ChatAPI - 私聊创建接口
 */
export const useFriendStore = defineStore('friend', () => {
  const router = useRouter()
  const { t } = i18n.global
  
  /**
   * 好友列表
   * 包含当前用户的所有好友信息
   */
  const friends = ref<Friend[]>([])

  /**
   * 好友申请列表
   * 包含接收到的所有好友请求（待处理、已通过、已拒绝）
   */
  const requests = ref<FriendRequest[]>([])

  /**
   * 加载状态
   * 用于控制加载动画
   */
  const loading = ref(false)

  /**
   * 待处理申请计数 (Computed)
   * 用于显示底部导航栏或侧边栏的徽章提醒
   */
  const pendingCount = computed(() => requests.value.filter(r => r.status === 0).length)

  /**
   * 获取好友列表
   * 
   * 业务逻辑：
   * - 设置 loading 状态
   * - 调用后端接口拉取最新好友列表
   * - 更新 store 中的 friends 数据
   */
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

  /**
   * 获取好友申请列表
   * 
   * 业务逻辑：
   * - 调用后端接口拉取好友申请
   * - 更新 store 中的 requests 数据
   * - 自动重新计算 pendingCount
   */
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

  /**
   * 发送好友申请
   * 
   * @param targetUid 目标用户 ID
   * @returns void (通过 Toast 提示结果)
   */
  const sendRequest = async (targetUid: string) => {
    const res = await sendFriendRequestApi({ targetUid })
    if (res.code === 200) {
      ElMessage.success(t('friend.request_sent'))
    } else {
      ElMessage.error(res.message || t('friend.request_failed'))
    }
  }

  /**
   * 处理好友申请（接受/拒绝）
   * 
   * 业务逻辑：
   * 1. 调用后端接口更新申请状态
   * 2. 成功后刷新申请列表（更新状态显示）
   * 3. 如果是接受申请，同时刷新好友列表
   * 
   * @param requestId 申请记录 ID
   * @param accept true=接受, false=拒绝
   */
  const handleRequest = async (requestId: number, accept: boolean) => {
    const res = await handleFriendRequestApi({ requestId, accept })
    if (res.code === 200) {
      ElMessage.success(accept ? t('friend.accepted') : t('friend.rejected'))
      await fetchRequests()
      if (accept) await fetchFriends()
    } else {
      ElMessage.error(res.message || t('friend.operation_failed'))
    }
  }

  /**
   * 删除好友
   * 
   * 业务逻辑：
   * 1. 调用后端接口解除好友关系
   * 2. 成功后刷新好友列表
   * 
   * @param friendUid 好友的用户 ID
   */
  const removeFriend = async (friendUid: string) => {
    const res = await removeFriendApi(friendUid)
    if (res.code === 200) {
      ElMessage.success(t('friend.friend_removed'))
      await fetchFriends()
    } else {
      ElMessage.error(res.message || t('friend.remove_failed'))
    }
  }

  /**
   * 发起私聊
   * 
   * 业务逻辑：
   * 1. 调用 getOrCreatePrivateChatApi 获取或创建私聊房间
   * 2. 成功后跳转到对应的聊天页面 (/chat/:chatCode)
   * 
   * @param friendUid 好友的用户 ID
   */
  const startChat = async (friendUid: string) => {
    try {
      const res = await getOrCreatePrivateChatApi({ targetUid: friendUid })
      if (res.code === 200 && res.data) {
        const chatCode = res.data
        router.push(`/chat/${chatCode}`)
      } else {
        ElMessage.error(res.message || t('friend.start_chat_failed'))
      }
    } catch {
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
