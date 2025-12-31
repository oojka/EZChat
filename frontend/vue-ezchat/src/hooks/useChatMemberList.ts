import {computed, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {useUserStore} from '@/stores/userStore.ts'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useWebsocketStore} from '@/stores/websocketStore.ts'

export const useChatMemberList = () => {
  const userStore = useUserStore()
  const { loginUserInfo, userStatusList } = storeToRefs(userStore)

  const roomStore = useRoomStore()
  const { currentRoom } = storeToRefs(roomStore)

  const websocketStore = useWebsocketStore()
  const { status } = storeToRefs(websocketStore)

  const statusMap = computed(() => {
    return new Map(userStatusList.value.map((s) => [s.uId, s.online]))
  })

  // --- 延迟排序逻辑：确保变灰后再移动 ---
  const delayedStatusMap = ref(new Map<string, boolean>())

  // 监听原始状态映射，实现 0.5s 延迟同步
  watch(statusMap, (newMap) => {
    // 立即处理新加入的成员，延迟处理状态变化
    newMap.forEach((online, uId) => {
      const currentDelayed = delayedStatusMap.value.get(uId)

      if (currentDelayed === undefined) {
        // 新成员：立即同步
        delayedStatusMap.value.set(uId, online)
      } else if (currentDelayed !== online) {
        // 状态变化：延迟 0.5s 同步，让变灰动画先执行
        setTimeout(() => {
          delayedStatusMap.value.set(uId, online)
        }, 500)
      }
    })
  }, { immediate: true, deep: true })

  const sortedChatMemberList = computed(() => {
    const members = currentRoom.value?.chatMembers
    if (!members) return []

    const currentUserId = loginUserInfo.value?.uId
    const sMap = statusMap.value
    const dMap = delayedStatusMap.value

    return [...members]
      .sort((a, b) => {
        // 1. 本人置顶
        if (a.uId === currentUserId) return -1
        if (b.uId === currentUserId) return 1

        // 2. 在线状态优先级 (使用延迟后的状态进行排序)
        const onlineA = dMap.get(a.uId) ?? a.online
        const onlineB = dMap.get(b.uId) ?? b.online

        if (onlineA !== onlineB) return onlineB ? 1 : -1

        // 3. 字母序
        return (a.nickname || '').localeCompare(b.nickname || '', 'ja-JP')
      })
      .map((m) => ({
        ...m,
        // 视图显示使用实时状态 (sMap)，确保变灰动画立即开始
        isOnline: Boolean(sMap.get(m.uId) ?? m.online),
      }))
  })

  const loginUserState = computed(() => {
    switch (status.value) {
      case 'OPEN':
        return { type: 'success' }
      case 'CONNECTING':
        return { type: 'warning' }
      case 'CLOSED':
        return { type: 'error' }
      default:
        return { type: 'info' }
    }
  })

  return {
    chat: currentRoom,
    sortedChatMemberList,
    loginUserInfo,
    loginUserState,
  }
}
