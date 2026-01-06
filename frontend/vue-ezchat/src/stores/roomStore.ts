import { computed, ref } from 'vue'
import { defineStore, storeToRefs } from 'pinia'
import type { ChatRoom, Message, Image, JoinChatReq, ValidateChatJoinReq } from '@/type'
import { initApi } from '@/api/AppInit.ts'
import { getChatMembersApi, joinChatApi } from '@/api/Chat'
import { validateChatJoinApi } from '@/api/Auth'
import { useUserStore } from '@/stores/userStore.ts'
import { useImageStore } from '@/stores/imageStore'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes'
import { useI18n } from 'vue-i18n'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
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
  const { t } = useI18n()
  const router = useRouter()

  const userStore = useUserStore()
  const { loginUserInfo, userStatusList } = storeToRefs(userStore)

  // 内部原始数据
  const _roomList = ref<ChatRoom[]>([])
  // 当前选中的房间代码
  const currentRoomCode = ref('')
  // 房间列表加载状态：用于 refresh 时避免 UI “先出现 1 条，再补齐”的割裂感
  const isRoomListLoading = ref(false)
  // 房间列表是否已完成一次加载（用于控制 updateRoomInfo 的行为）
  const hasRoomListLoaded = ref(false)
  // 列表尚未加载完成时的“待合并房间信息”（例如 message 接口返回的 chatRoom）
  const pendingRoomInfoMap = new Map<string, ChatRoom>()

  const createChatDialogVisible = ref(false)
  const joinChatDialogVisible = ref(false)

  /**
   * 当前房间成员列表加载状态（按 chatCode）
   *
   * 业务目的：右侧成员栏需要一个“独立于 messageList”的 loading 状态，用于局部遮蔽。
   */
  const memberListLoadingMap = ref<Record<string, boolean>>({})
  /**
   * 重置 Store 内存状态
   *
   * 业务场景：App 初始化/账号切换前，清空房间列表与当前选中房间，防止旧数据残留。
   */
  const resetState = () => {
    _roomList.value = []
    currentRoomCode.value = ''
    isRoomListLoading.value = false
    hasRoomListLoaded.value = false
    pendingRoomInfoMap.clear()
    memberListLoadingMap.value = {}
  }


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
   * 类型守卫：验证 avatar 是否为有效的 Image 对象
   */
  const isImage = (avatar: Image | null | undefined): avatar is Image => {
    return avatar !== null && avatar !== undefined
  }

  /**
   * 初始化房间列表及用户在线状态
   */
  const initRoomList = async () => {
    try {
      isRoomListLoading.value = true
      const imageStore = useImageStore()
      const prevAvatars = _roomList.value.map(r => r.avatar).filter(isImage)
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

      // 1.5) 合并“加载期间缓存的 roomInfo”（避免 refresh 时先插入 1 条）
      if (pendingRoomInfoMap.size > 0 && _roomList.value.length > 0) {
        for (const [code, info] of pendingRoomInfoMap.entries()) {
          const target = _roomList.value.find(r => r.chatCode === code)
          if (target) {
            Object.assign(target, {
              ...info,
              chatMembers: info.chatMembers || target.chatMembers,
            })
          }
        }
        pendingRoomInfoMap.clear()
      }

      // 2) 列表头像缩略图：在 Store 更新后异步预取 blob（不阻塞主流程）
      const rooms = _roomList.value || []
      const allAvatars = rooms.map(r => r.avatar).filter(isImage)
      // prefetchThumbs 内部已自动去重，无需手动去重
      imageStore.revokeUnusedBlobs(prevAvatars, allAvatars)
      imageStore.prefetchThumbs(allAvatars, 6)
    } catch (e) {
      console.error('[ERROR] [RoomStore] Init API Error:', e)
    } finally {
      isRoomListLoading.value = false
      hasRoomListLoaded.value = true
    }
  }

  /**
   * 更新或新增房间信息
   */
  const updateRoomInfo = (newRoomInfo: ChatRoom) => {
    if (!newRoomInfo || !newRoomInfo.chatCode) return
    const imageStore = useImageStore()

    // 列表尚未加载完成时：只缓存，不插入（避免 refresh 时只出现“当前房间 1 条”）
    if (!hasRoomListLoaded.value && _roomList.value.length === 0) {
      pendingRoomInfoMap.set(newRoomInfo.chatCode, newRoomInfo)
      // 头像仍可提前预取（不影响列表长度）
      if (newRoomInfo.avatar) imageStore.ensureThumbBlobUrl(newRoomInfo.avatar).then(() => { })
      return
    }
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

    // 房间信息更新后：按需预取房间头像与成员头像缩略图（避免刷新/切换时头像加载慢）
    if (newRoomInfo.avatar) imageStore.ensureThumbBlobUrl(newRoomInfo.avatar).then(() => { })
    const members = newRoomInfo.chatMembers || []
    // prefetchThumbs 内部已自动去重，无需手动去重
    imageStore.prefetchThumbs(members.map(m => m.avatar), 6)
  }

  /**
   * 获取聊天室成员列表（按 chatCode 懒加载）
   *
   * 业务目的：
   * - refresh 初始化只拉 chatList；成员列表在进入房间/展开右侧栏时再请求
   * - 避免一次性拉取所有群成员导致网络与图片预取拥塞
   *
   * @param chatCode 聊天室对外 ID
   */
  const fetchRoomMembers = async (chatCode: string) => {
    if (!chatCode) return
    const target = _roomList.value.find(r => r.chatCode === chatCode)
    if (!target) return
    if (target.chatMembers && target.chatMembers.length > 0) return

    // 避免重复请求
    if (memberListLoadingMap.value[chatCode]) return
    memberListLoadingMap.value = { ...memberListLoadingMap.value, [chatCode]: true }
    try {
      const res = await getChatMembersApi(chatCode)
      const members = res?.data || []
      target.chatMembers = members
      target.memberCount = members.length
      target.onLineMemberCount = members.filter(m => m.online).length

      // 成员头像缩略图预取：异步触发，不阻塞 UI
      // prefetchThumbs 内部已自动去重，无需手动去重
      const imageStore = useImageStore()
      imageStore.prefetchThumbs(members.map(m => m.avatar).filter(isImage), 6)
    } catch (e) {
      console.error('[ERROR] [RoomStore] Fetch members failed:', e)
    } finally {
      const { [chatCode]: _, ...rest } = memberListLoadingMap.value
      memberListLoadingMap.value = rest
    }
  }

  /**
   * 当前房间成员列表是否加载中
   */
  const isCurrentRoomMembersLoading = computed(() => {
    const code = currentRoomCode.value
    if (!code) return false
    return Boolean(memberListLoadingMap.value[code])
  })

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
    // 业务目的：列表页需要展示"最后一条消息 + 最后活跃时间 + 未读数"
    const room = _roomList.value.find(r => r.chatCode === message.chatCode)
    if (room) {
      room.lastActiveAt = message.createTime
      room.lastMessage = message

      if (room.chatCode !== currentRoomCode.value) {
        room.unreadCount = (room.unreadCount || 0) + 1
      }
    }
  }

  /**
   * 正式用户加入聊天室
   * 
   * 业务逻辑：
   * 1. 调用后端 API 加入聊天室
   * 2. 加入成功后自动刷新房间列表
   * 
   * @param data 加入聊天室请求数据
   * @returns 是否加入成功
   */
  const joinChat = async (data: JoinChatReq): Promise<boolean> => {
    try {
      const result = await joinChatApi(data)
      if (result && result.status === 1) {
        // 加入成功后显示提示信息
        ElMessage.success('加入房间成功')
        // 如果当前页面是聊天页，则自动刷新房间列表
        if (router.currentRoute.value.path === '/chat') {
          await initRoomList()
        }
        return true
      }
      return false
    } catch (e) {
      if (e instanceof Error && e.message === 'IS_ALREADY_JOINED') {
        // 用户已经加入此房间，显示提示并返回 true（表示用户已在房间）
        await showAlertDialog({
          message: t('api.you_have_already_in_this_room'),
          type: 'warning',
        })
        return true
      }
      if (isAppError(e)) {
        throw e
      }
      throw createAppError(
        ErrorType.UNKNOWN,
        'Join chat failed',
        {
          severity: ErrorSeverity.ERROR,
          component: 'roomStore',
          action: 'joinChat',
          originalError: e
        }
      )
    }
  }

  /**
   * 验证房间访问权限
   * 
   * 业务逻辑：
   * 1. 调用验证 API 检查房间信息
   * 2. 将验证结果存储到 userStore
   * 3. 返回验证成功的房间信息
   * 
   * @param req 验证请求对象
   * @returns 验证成功的房间信息
   */
  const validateRoomAccess = async (req: ValidateChatJoinReq): Promise<ChatRoom> => {
    try {
      const result = await validateChatJoinApi(req)
      
      if (result && result.data && result.data.chatCode) {
        // 验证成功，将信息存储到 userStore
        userStore.setValidatedJoinChatInfo(req, result.data)
        return result.data
      }
      
      throw createAppError(
        ErrorType.NETWORK,
        'Room validation failed',
        {
          severity: ErrorSeverity.ERROR,
          component: 'roomStore',
          action: 'validateRoomAccess'
        }
      )
    } catch (e) {
      if (isAppError(e)) {
        throw e
      }
      throw createAppError(
        ErrorType.NETWORK,
        'Room validation failed',
        {
          severity: ErrorSeverity.ERROR,
          component: 'roomStore',
          action: 'validateRoomAccess',
          originalError: e
        }
      )
    }
  }

    return {
      roomList,
      currentRoomCode,
      currentRoom,
      isRoomListLoading,
      createChatDialogVisible,
      joinChatDialogVisible,
      getRoomByCode, // 导出此方法
      initRoomList,
      updateRoomInfo,
      updateMemberStatus,
      updateRoomPreview,
      fetchRoomMembers,
      isCurrentRoomMembersLoading,
      joinChat, // 加入聊天室
      validateRoomAccess, // 验证房间访问权限
      resetState,
    }
  })
