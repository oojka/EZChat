import { computed, ref } from 'vue'
import { defineStore, storeToRefs } from 'pinia'
import type { ChatRoom, ChatMember, Message, Image, JoinChatReq, ValidateChatJoinReq, MemberLeaveBroadcastPayload, MemberRemovedBroadcastPayload, OwnerTransferBroadcastPayload, RoomDisbandBroadcastPayload, LoginUserInfo } from '@/type'
import { initApi } from '@/api/AppInit.ts'
import { getChatMembersApi, joinChatApi } from '@/api/Chat'
import { validateChatJoinApi } from '@/api/Auth'
import { useUserStore } from '@/stores/userStore.ts'
import { useImageStore } from '@/stores/imageStore'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes'
import i18n from '@/i18n'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'
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
  const { t } = i18n.global
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
  const roomSettingsDialogVisible = ref(false)

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

  const buildRoomAvatarKey = (chatCode?: string) => {
    return chatCode ? `room:${chatCode}` : ''
  }

  const buildUserAvatarKey = (uid?: string) => {
    return uid ? `user:${uid}` : ''
  }

  const resolveRoomAvatar = (room: ChatRoom, imageStore: ReturnType<typeof useImageStore>) => {
    if (!room.avatar) return
    const resolved = imageStore.resolveAvatarFromCache(buildRoomAvatarKey(room.chatCode), room.avatar)
    if (resolved) {
      room.avatar = resolved
    }
  }

  const resolveMemberAvatar = (member: ChatMember, imageStore: ReturnType<typeof useImageStore>) => {
    if (!member.avatar) return
    const resolved = imageStore.resolveAvatarFromCache(buildUserAvatarKey(member.uid), member.avatar)
    if (resolved) {
      member.avatar = resolved
    }
  }

  const collectAvatarCacheKeys = () => {
    const keys = new Set<string>()
    _roomList.value.forEach((room) => {
      const roomKey = buildRoomAvatarKey(room.chatCode)
      if (roomKey) keys.add(roomKey)
      room.chatMembers?.forEach((member) => {
        const memberKey = buildUserAvatarKey(member.uid)
        if (memberKey) keys.add(memberKey)
      })
    })
    for (const pending of pendingRoomInfoMap.values()) {
      const roomKey = buildRoomAvatarKey(pending.chatCode)
      if (roomKey) keys.add(roomKey)
      pending.chatMembers?.forEach((member) => {
        const memberKey = buildUserAvatarKey(member.uid)
        if (memberKey) keys.add(memberKey)
      })
    }
    if (loginUserInfo.value?.uid) {
      const selfKey = buildUserAvatarKey(loginUserInfo.value.uid)
      if (selfKey) keys.add(selfKey)
    }
    return keys
  }

  /**
   * 初始化房间列表及用户在线状态
   */
  const initRoomList = async () => {
    try {
      isRoomListLoading.value = true
      const imageStore = useImageStore()
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
      rooms.forEach((room) => {
        resolveRoomAvatar(room, imageStore)
        room.chatMembers?.forEach((member) => resolveMemberAvatar(member, imageStore))
      })
      const roomAvatars = rooms.map(r => r.avatar).filter(isImage)
      const memberAvatars = rooms.flatMap((room) => room.chatMembers?.map((member) => member.avatar) || []).filter(isImage)
      // prefetchThumbs 内部已自动去重，无需手动去重
      imageStore.prefetchThumbs([...roomAvatars, ...memberAvatars], 6)
      imageStore.pruneAvatarCache(collectAvatarCacheKeys())
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
    resolveRoomAvatar(newRoomInfo, imageStore)
    newRoomInfo.chatMembers?.forEach((member) => resolveMemberAvatar(member, imageStore))

    // 列表尚未加载完成时：只缓存，不插入（避免 refresh 时只出现“当前房间 1 条”）
    if (!hasRoomListLoaded.value && _roomList.value.length === 0) {
      pendingRoomInfoMap.set(newRoomInfo.chatCode, newRoomInfo)
      // 头像仍可提前预取（不影响列表长度）
      if (newRoomInfo.avatar) imageStore.ensureThumbBlobUrl(newRoomInfo.avatar).then(() => { })
      imageStore.pruneAvatarCache(collectAvatarCacheKeys())
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
    imageStore.pruneAvatarCache(collectAvatarCacheKeys())
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
      const imageStore = useImageStore()
      members.forEach((member) => resolveMemberAvatar(member, imageStore))
      target.chatMembers = members
      target.memberCount = members.length
      target.onLineMemberCount = members.filter(m => m.online).length

      // 成员头像缩略图预取：异步触发，不阻塞 UI
      // prefetchThumbs 内部已自动去重，无需手动去重
      imageStore.prefetchThumbs(members.map(m => m.avatar).filter(isImage), 6)
      imageStore.pruneAvatarCache(collectAvatarCacheKeys())
    } catch (e) {
      console.error('[ERROR] [RoomStore] Fetch members failed:', e)
    } finally {
      const { [chatCode]: _unusedLoading, ...rest } = memberListLoadingMap.value
      void _unusedLoading
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
   * 添加单个成员到房间 (WeSocket 推送)
   */
  /**
   * 添加单个成员到房间 (WeSocket 推送)
   */
  const addRoomMember = (member: ChatMember) => {
    if (!member || !member.chatCode) return
    const room = _roomList.value.find(r => r.chatCode === member.chatCode)
    const imageStore = useImageStore()
    resolveMemberAvatar(member, imageStore)
    if (room) {
      // 1. 总是更新统计数据
      room.memberCount = (room.memberCount || 0) + 1
      if (member.online) {
        room.onLineMemberCount = (room.onLineMemberCount || 0) + 1
        // 更新全局在线表
        const exists = userStatusList.value.find(u => u.uid === member.uid)
        if (!exists) {
          userStatusList.value.push({ uid: member.uid, online: true, updateTime: new Date().toISOString() })
        } else {
          exists.online = true
        }
      }

      // 2. 只有当列表已被加载过（存在且不为空）时，才追加新成员
      // 防止：列表为空 -> 插入1个 -> fetchRoomMembers 误判已加载 -> 只显示1个成员
      if (room.chatMembers && room.chatMembers.length > 0) {
        // 避免重复
        if (!room.chatMembers.some(m => m.uid === member.uid)) {
          room.chatMembers.push(member)
        }
      }
    }
    imageStore.pruneAvatarCache(collectAvatarCacheKeys())
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
   * 同步本人在成员列表中的信息（昵称/头像）
   */
  const syncLoginMemberInfo = (info?: LoginUserInfo) => {
    const source = info ?? loginUserInfo.value
    if (!source?.uid) return

    const imageStore = useImageStore()
    const avatarKey = buildUserAvatarKey(source.uid)
    const resolvedAvatar = source.avatar
      ? imageStore.resolveAvatarFromCache(avatarKey, source.avatar) || source.avatar
      : source.avatar

    const updateMember = (member: ChatMember) => {
      if (member.uid !== source.uid) return
      member.nickname = source.nickname
      if (resolvedAvatar) {
        member.avatar = resolvedAvatar
      }
    }

    _roomList.value.forEach((room) => {
      room.chatMembers?.forEach(updateMember)
    })
    for (const room of pendingRoomInfoMap.values()) {
      room.chatMembers?.forEach(updateMember)
    }

    if (resolvedAvatar) {
      imageStore.ensureThumbBlobUrl(resolvedAvatar).then(() => { })
    }
    imageStore.pruneAvatarCache(collectAvatarCacheKeys())
  }

  /**
   * 处理成员退群广播
   *
   * @param payload 退群广播数据
   */
  const handleMemberLeave = (payload: MemberLeaveBroadcastPayload) => {
    const room = _roomList.value.find(r => r.chatCode === payload.chatCode)
    if (!room) return

    if (room.chatMembers && room.chatMembers.length > 0) {
      room.chatMembers = room.chatMembers.filter(m => m.uid !== payload.uid)
      room.memberCount = room.chatMembers.length
      room.onLineMemberCount = room.chatMembers.filter(m => {
        const status = userStatusList.value.find(s => s.uid === m.uid)
        return status?.online
      }).length
      const imageStore = useImageStore()
      imageStore.pruneAvatarCache(collectAvatarCacheKeys())
      return
    }

    room.memberCount = Math.max((room.memberCount || 0) - 1, 0)
    if (room.onLineMemberCount && room.onLineMemberCount > 0) {
      const status = userStatusList.value.find(s => s.uid === payload.uid)
      if (status?.online) {
        room.onLineMemberCount = Math.max(room.onLineMemberCount - 1, 0)
      }
    }
    const imageStore = useImageStore()
    imageStore.pruneAvatarCache(collectAvatarCacheKeys())
  }

  /**
   * 处理成员被移除广播
   *
   * @param payload 被移除广播数据
   */
  const handleMemberRemoved = async (payload: MemberRemovedBroadcastPayload) => {
    const room = _roomList.value.find(r => r.chatCode === payload.chatCode)
    if (!room) return

    const removedUid = payload.removedUid
    const isSelfRemoved = loginUserInfo.value?.uid === removedUid

    if (room.chatMembers && room.chatMembers.length > 0) {
      room.chatMembers = room.chatMembers.filter(m => m.uid !== removedUid)
      room.memberCount = room.chatMembers.length
      room.onLineMemberCount = room.chatMembers.filter(m => {
        const status = userStatusList.value.find(s => s.uid === m.uid)
        return status?.online
      }).length
    } else {
      room.memberCount = Math.max((room.memberCount || 0) - 1, 0)
      if (room.onLineMemberCount && room.onLineMemberCount > 0) {
        const status = userStatusList.value.find(s => s.uid === removedUid)
        if (status?.online) {
          room.onLineMemberCount = Math.max(room.onLineMemberCount - 1, 0)
        }
      }
    }

    const imageStore = useImageStore()
    imageStore.pruneAvatarCache(collectAvatarCacheKeys())

    if (!isSelfRemoved) return

    const roomName = room.chatName || payload.chatCode
    await showAlertDialog({
      message: t('chat.member_removed_alert', [roomName, payload.operatorNickname]),
      type: 'warning',
    })

    const currentChatParam = router.currentRoute.value.params.chatCode
    const activeChatCode = typeof currentChatParam === 'string' ? currentChatParam : ''
    if (activeChatCode === payload.chatCode) {
      currentRoomCode.value = ''
      await router.push('/chat')
    }
    await initRoomList()
  }

  /**
   * 处理群主转让广播
   *
   * @param payload 群主转让广播数据
   */
  const handleOwnerTransfer = (payload: OwnerTransferBroadcastPayload) => {
    const room = _roomList.value.find(r => r.chatCode === payload.chatCode)
    if (room) {
      room.ownerUid = payload.newOwnerUid
    }

    if (loginUserInfo.value?.uid === payload.newOwnerUid) {
      const roomName = room?.chatName || payload.chatCode
      showAlertDialog({
        message: t('chat.owner_transfer_alert', [roomName]),
        type: 'info',
      })
    }
  }

  /**
   * 处理群聊解散广播
   *
   * @param payload 解散广播数据
   */
  const handleRoomDisband = async (payload: RoomDisbandBroadcastPayload) => {
    const room = _roomList.value.find(r => r.chatCode === payload.chatCode)
    const roomName = room?.chatName || payload.chatCode
    await showAlertDialog({
      message: t('chat.room_disband_alert', [roomName]),
      type: 'warning',
    })

    const currentChatParam = router.currentRoute.value.params.chatCode
    const activeChatCode = typeof currentChatParam === 'string' ? currentChatParam : ''
    if (activeChatCode === payload.chatCode) {
      await router.push('/chat')
    }
    await initRoomList()
  }

  /**
   * 处理加入成功后的导航逻辑
   * @param chatCode 目标房间代码
   */
  const processJoinNavigation = async (chatCode?: string) => {
    if (chatCode) {
      await router.push(`/chat/${chatCode}`)
    } else {
      await router.push('/chat')
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
  const joinChat = async (data: JoinChatReq): Promise<'SUCCESS' | 'ALREADY_JOINED' | false> => {
    try {
      const result = await joinChatApi(data)
      if (result && result.status === 1) {
        // 加入成功后显示提示信息
        // ElMessage.success('加入房间成功') // 移交给上层决定是否显示
        // 如果当前页面是聊天页，则自动刷新房间列表
        if (router.currentRoute.value.path.startsWith('/chat')) {
          // 只有在已登录且在聊天页时刷新才有意义，
          // 但 joinChat 通常是在 Join 页调用的。
          // 无论如何在跳转前刷新没坏处，或者跳转后刷新。
          // 这里保留原逻辑
          await initRoomList()
        }
        return 'SUCCESS'
      }
      return false
    } catch (e) {
      if (e instanceof Error && e.message === 'IS_ALREADY_JOINED') {
        // 用户已经加入此房间，显示提示并返回 true（表示用户已在房间）
        // Global Loading (z-99999) is active, but Main.css forces .el-overlay to z-100000
        // so this Dialog is now clickable. We await user confirmation before proceeding.
        await showAlertDialog({
          message: t('api.you_have_already_in_this_room'),
          type: 'warning',
        })
        return 'ALREADY_JOINED'
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
  const validateRoomAccess = async (req: ValidateChatJoinReq): Promise<ChatRoom | null> => {
    try {
      const result = await validateChatJoinApi(req)
      if (result && result.data && result.data.chatCode) {
        // 验证成功，将信息存储到 userStore
        userStore.setValidatedJoinChatInfo(req, result.data)
        return result.data
      }
      return null;
    } catch (e) {
      if (isAppError(e)) {
        throw e
      }
      const errorMsg = e instanceof Error ? e.message : 'Room validation failed'
      throw createAppError(
        ErrorType.NETWORK,
        errorMsg,
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
    roomSettingsDialogVisible,
    getRoomByCode, // 导出此方法
    initRoomList,
    updateRoomInfo,
    updateMemberStatus,
    syncLoginMemberInfo,
    addRoomMember,
    updateRoomPreview,
    handleMemberLeave,
    handleMemberRemoved,
    handleOwnerTransfer,
    handleRoomDisband,
    fetchRoomMembers,
    isCurrentRoomMembersLoading,
    joinChat, // 加入聊天室
    processJoinNavigation, // 处理加入后跳转
    validateRoomAccess, // 验证房间访问权限
    resetState,
  }
})
