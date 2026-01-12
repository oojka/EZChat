/**
 * 聊天室成员列表 Composable
 *
 * 核心职责：
 * - 提供排序后的成员列表（本人置顶 → 在线优先 → 字母序）
 * - 实现在线状态延迟同步（先变灰再移动，避免闪烁）
 * - 提供当前用户的连接状态指示
 *
 * 使用示例：
 * ```vue
 * const { sortedChatMemberList, loginUserState } = useChatMemberList()
 * ```
 *
 * @module useChatMemberList
 */
import {computed, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {useUserStore} from '@/stores/userStore.ts'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useWebsocketStore} from '@/stores/websocketStore.ts'

/**
 * 聊天室成员列表业务逻辑 Hook
 *
 * @returns 排序成员列表、当前用户状态等
 */
export const useChatMemberList = () => {
  const userStore = useUserStore()
  const { loginUserInfo, userStatusList } = storeToRefs(userStore)

  const roomStore = useRoomStore()
  const { currentRoom } = storeToRefs(roomStore)

  const websocketStore = useWebsocketStore()
  const { status } = storeToRefs(websocketStore)

  const statusMap = computed(() => {
    return new Map(userStatusList.value.map((s) => [s.uid, s.online]))
  })

  // --- 延迟排序逻辑：确保变灰后再移动 ---
  const delayedStatusMap = ref(new Map<string, boolean>())

  // 监听原始状态映射，实现 0.5s 延迟同步
  watch(statusMap, (newMap) => {
    // 立即处理新加入的成员，延迟处理状态变化
    newMap.forEach((online, uid) => {
      const currentDelayed = delayedStatusMap.value.get(uid)

      if (currentDelayed === undefined) {
        // 新成员：立即同步
        delayedStatusMap.value.set(uid, online)
      } else if (currentDelayed !== online) {
        // 状态变化：延迟 0.5s 同步，让变灰动画先执行
        setTimeout(() => {
          delayedStatusMap.value.set(uid, online)
        }, 500)
      }
    })
  }, { immediate: true, deep: true })

  const sortedChatMemberList = computed(() => {
    const members = currentRoom.value?.chatMembers
    if (!members) return []

    const currentUserId = loginUserInfo.value?.uid
    const sMap = statusMap.value
    const dMap = delayedStatusMap.value

    return [...members]
      .sort((a, b) => {
        // 1. 本人置顶
        if (a.uid === currentUserId) return -1
        if (b.uid === currentUserId) return 1

        // 2. 在线状态优先级 (使用延迟后的状态进行排序)
        const onlineA = dMap.get(a.uid) ?? a.online
        const onlineB = dMap.get(b.uid) ?? b.online

        if (onlineA !== onlineB) return onlineB ? 1 : -1

        // 3. 字母序
        return (a.nickname || '').localeCompare(b.nickname || '', 'ja-JP')
      })
      .map((m) => ({
        ...m,
        // 视图显示使用实时状态 (sMap)，确保变灰动画立即开始
        online: Boolean(sMap.get(m.uid) ?? m.online),
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
