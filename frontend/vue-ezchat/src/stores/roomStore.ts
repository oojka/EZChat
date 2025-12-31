import {computed, ref} from 'vue'
import {defineStore, storeToRefs} from 'pinia'
import type {ChatRoom, Message} from '@/type'
import {initApi} from '@/api/AppInit.ts'
import {useUserStore} from '@/stores/userStore.ts'

export const useRoomStore = defineStore('room', () => {
  // =========================================
  // 1. 基础依赖与状态 (State)
  // =========================================
  const userStore = useUserStore()
  const { loginUserInfo, userStatusList } = storeToRefs(userStore)

  // 内部原始数据
  const _roomList = ref<ChatRoom[]>([])
  // 当前选中的房间代码
  const currentRoomCode = ref('')

  // =========================================
  // 2. 计算属性 (Getters)
  // =========================================

  // 排序后的房间列表 (按最后活跃时间倒序)
  const roomList = computed<ChatRoom[]>(() => {
    return [..._roomList.value].sort((a, b) => {
      const timeA = a.lastActiveAt ? new Date(a.lastActiveAt).getTime() : 0
      const timeB = b.lastActiveAt ? new Date(b.lastActiveAt).getTime() : 0
      return timeB - timeA
    })
  })

  // 根据 Code 获取房间对象
  const getRoomByCode = (code: string) => {
    return _roomList.value.find((room) => room.chatCode === code)
  }

  // 当前激活的房间对象
  const currentRoom = computed(() => {
    return getRoomByCode(currentRoomCode.value)
  })

  // =========================================
  // 3. 核心操作 (Actions)
  // =========================================

  /**
   * 初始化房间列表及用户在线状态
   */
  const initRoomList = async () => {
    try {
      const result = await initApi()
      if (result?.data) {
        _roomList.value = result.data.chatList ?? []
        userStatusList.value = result.data.userStatusList ?? []

        if (
          loginUserInfo.value?.uid &&
          !userStatusList.value.some((u) => u.uid === loginUserInfo.value?.uid)
        ) {
          userStatusList.value.push({
            uid: loginUserInfo.value.uid,
            online: true,
            updateTime: new Date().toISOString(),
          })
        }
      }
    } catch (e) {
      console.error('[ERROR] [RoomStore] Init API Error:', e)
    }
  }

  /**
   * 更新或新增房间信息
   */
  const updateRoomInfo = (newRoomInfo: ChatRoom) => {
    if (!newRoomInfo || !newRoomInfo.chatCode) return
    const target = _roomList.value.find(r => r.chatCode === newRoomInfo.chatCode)
    if (target) {
      Object.assign(target, {
        ...newRoomInfo,
        chatMembers: newRoomInfo.chatMembers || target.chatMembers,
      })
    } else {
      _roomList.value.push(newRoomInfo)
    }
  }

  /**
   * 更新用户在线状态并重新计算房间在线人数
   */
  const updateMemberStatus = (uid: string, isOnline: boolean) => {
    const targetStatus = userStatusList.value.find((s) => s.uid === uid)
    if (targetStatus) {
      targetStatus.online = isOnline
    } else {
      userStatusList.value.push({ uid, online: isOnline, updateTime: new Date().toISOString() })
    }

    _roomList.value.forEach((room) => {
      if (room.chatMembers?.some((m) => m.uid === uid)) {
        room.onLineMemberCount = room.chatMembers.filter(m => {
          const status = userStatusList.value.find(s => s.uid === m.uid)
          return status?.online
        }).length
      }
    })
  }

  /**
   * 更新房间预览信息
   */
  const updateRoomPreview = (message: Message) => {
    const room = _roomList.value.find(r => r.chatCode === message.chatCode)
    if (room) {
      room.lastActiveAt = message.createTime
      room.lastMessage = message

      if (room.chatCode !== currentRoomCode.value) {
        room.unreadCount = (room.unreadCount || 0) + 1
      }
    }
  }

  return {
    roomList,
    currentRoomCode,
    currentRoom,
    getRoomByCode, // 导出此方法
    initRoomList,
    updateRoomInfo,
    updateMemberStatus,
    updateRoomPreview,
  }
})
