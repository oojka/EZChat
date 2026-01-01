import {computed, ref} from 'vue'
import {defineStore, storeToRefs} from 'pinia'
import type {ChatRoom, Message} from '@/type'
import {initApi} from '@/api/AppInit.ts'
import {useUserStore} from '@/stores/userStore.ts'

/**
 * RoomStore：管理聊天室列表与“当前所在房间”
 *
 * 业务职责：
 * - 维护房间列表（排序、更新、未读数）
 * - 维护当前激活房间 code（用于消息列表、心跳等）
 * - 维护成员在线状态（WS USER_STATUS 推送后更新）
 */
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
    // 业务目的：列表总是展示“最新活跃”的房间在最上方
    return [..._roomList.value].sort((a, b) => {
      const timeA = a.lastActiveAt ? new Date(a.lastActiveAt).getTime() : 0
      const timeB = b.lastActiveAt ? new Date(b.lastActiveAt).getTime() : 0
      return timeB - timeA
    })
  })

  /**
   * 根据 chatCode 获取房间对象
   *
   * @param code chatCode（8 位数字字符串）
   */
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
      // 1) 从后端拉取：chatList + userStatusList
      const result = await initApi()
      if (result?.data) {
        _roomList.value = result.data.chatList ?? []
        userStatusList.value = result.data.userStatusList ?? []

        // 2) 确保“自己”一定在在线状态表里（避免服务端未返回自己状态导致 UI 显示异常）
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
    // 业务目的：进入房间后拿到更完整的 ChatRoom 信息（例如成员列表），需要覆盖到列表里
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
    // 1) 更新全局在线状态表（来自 WS 推送）
    const targetStatus = userStatusList.value.find((s) => s.uid === uid)
    if (targetStatus) {
      targetStatus.online = isOnline
    } else {
      userStatusList.value.push({ uid, online: isOnline, updateTime: new Date().toISOString() })
    }

    // 2) 对受影响的房间重新计算在线人数（只更新包含该成员的房间）
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
    // 业务目的：列表页需要展示“最后一条消息 + 最后活跃时间 + 未读数”
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
