/**
 * 房间邀请链接管理 Composable
 *
 * 核心职责：
 * - 管理邀请链接列表（获取、创建、撤销）
 * - 处理邀请链接过期时间设置
 * - 提供一次性链接开关
 * - 执行复制邀请链接到剪贴板
 *
 * 使用示例：
 * ```vue
 * const { inviteList, createInvite, copyInviteUrl } = useRoomInviteManager()
 * ```
 *
 * @module useRoomInviteManager
 */
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { createChatInviteApi, getChatInvitesApi, revokeChatInviteApi } from '@/api/Chat'
import type { ChatInvite } from '@/type'
import { isChatInvite, isChatInviteList } from '@/utils/validators'
import { showConfirmDialog } from '@/components/dialogs/confirmDialog'

/** 邀请链接基础 URL */
const INVITE_BASE_URL = 'https://ez-chat.oojka.com/invite/'
/** 默认过期时间（分钟）：7天 */
const DEFAULT_EXPIRY_MINUTES = 10080
/** 最大活跃邀请链接数量 */
const MAX_ACTIVE_INVITES = 5

/**
 * 房间邀请链接管理业务逻辑 Hook
 *
 * @returns 邀请列表、创建/撤销方法、复制方法等
 */
export const useRoomInviteManager = () => {
  const roomStore = useRoomStore()
  const { currentRoom, roomSettingsDialogVisible } = storeToRefs(roomStore)
  const { t } = useI18n()

  /** 邀请链接列表数据 */
  const inviteList = ref<ChatInvite[]>([])
  /** 加载状态 */
  const isLoading = ref(false)
  /** 创建中状态 */
  const isCreating = ref(false)
  /** 当前正在撤销的邀请ID */
  const revokingId = ref<number | null>(null)

  /** 自定义过期日期选择 */
  const selectedDate = ref<Date | null>(null)
  /** 预设过期时间选项 (1/7/30天) */
  const selectedDateRadio = ref<1 | 7 | 30 | null>(7)
  /** 是否为一次性链接开关 */
  const oneTimeLink = ref(false)
  /** 最终计算的过期时间（分钟） */
  const joinLinkExpiryMinutes = ref<number | null>(DEFAULT_EXPIRY_MINUTES)

  /** 二维码弹窗可见状态 */
  const qrDialogVisible = ref(false)
  /** 二维码弹窗显示的 URL */
  const qrDialogUrl = ref('')

  const tf = (key: string, fallback: string) => {
    const translated = t(key)
    const result = typeof translated === 'string' ? translated : String(translated)
    return result === key ? fallback : result
  }

  const resetForm = () => {
    selectedDate.value = null
    selectedDateRadio.value = 7
    oneTimeLink.value = false
    joinLinkExpiryMinutes.value = DEFAULT_EXPIRY_MINUTES
  }

  watch(selectedDateRadio, (newVal) => {
    if (newVal) {
      selectedDate.value = null
      joinLinkExpiryMinutes.value = newVal * 24 * 60
    }
  })

  watch(selectedDate, (newVal) => {
    if (newVal) {
      selectedDateRadio.value = null
      const diffMs = newVal.getTime() - Date.now()
      joinLinkExpiryMinutes.value = Math.max(1, Math.floor(diffMs / 60000))
    } else if (!selectedDateRadio.value) {
      selectedDateRadio.value = 7
    }
  })

  const disabledDate = (time: Date) => {
    const now = new Date()
    const startOfToday = new Date(now.setHours(0, 0, 0, 0))
    const thirtyDaysLater = new Date(startOfToday.getTime() + 31 * 24 * 60 * 60 * 1000)
    return time.getTime() < startOfToday.getTime() || time.getTime() > thirtyDaysLater.getTime()
  }

  const inviteCount = computed(() => inviteList.value.length)
  const canCreate = computed(() => inviteCount.value < MAX_ACTIVE_INVITES && !isCreating.value)
  const inviteLimitTip = computed(() => t('room_settings.invite_limit_tip', {
    count: inviteCount.value,
    max: MAX_ACTIVE_INVITES,
  }))

  const buildInviteUrl = (inviteCode: string) => `${INVITE_BASE_URL}${inviteCode}`

  const formatDateTime = (value: string) => {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const pad = (num: number) => String(num).padStart(2, '0')
    const year = date.getFullYear()
    const month = pad(date.getMonth() + 1)
    const day = pad(date.getDate())
    const hours = pad(date.getHours())
    const minutes = pad(date.getMinutes())
    return `${year}-${month}-${day} ${hours}:${minutes}`
  }

  /**
   * 获取房间的所有有效邀请链接
   */
  const fetchInvites = async () => {
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return

    isLoading.value = true
    try {
      const res = await getChatInvitesApi(chatCode)
      const data = res?.data
      inviteList.value = isChatInviteList(data) ? data : []
    } catch (error) {
      console.error('[ERROR] [RoomInvite] Failed to fetch invites:', error)
      inviteList.value = []
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 创建新的邀请链接
   *
   * 业务逻辑：
   * - 检查当前活跃链接数量是否达标
   * - 根据一次性开关设置最大使用次数
   * - 计算过期时间（分钟）
   *
   * @returns Promise<boolean> 创建是否成功
   */
  const createInvite = async (): Promise<boolean> => {
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return false
    // 1. 检查数量上限
    if (inviteCount.value >= MAX_ACTIVE_INVITES) {
      ElMessage.warning(tf('room_settings.invite_limit_reached', '已达到邀请码上限'))
      return false
    }

    isCreating.value = true
    try {
      // 2. 准备参数：一次性标记和过期时间
      const maxUses = oneTimeLink.value ? 1 : 0
      const expiryMinutes = joinLinkExpiryMinutes.value ?? DEFAULT_EXPIRY_MINUTES
      
      // 3. 调用创建 API
      const res = await createChatInviteApi(chatCode, {
        joinLinkExpiryMinutes: expiryMinutes,
        maxUses,
      })
      const data = res?.data
      if (!isChatInvite(data)) {
        throw new Error('Invalid invite response')
      }
      
      // 4. 更新列表（新创建的排在最前）
      inviteList.value = [data, ...inviteList.value]
      ElMessage.success(tf('room_settings.invite_create_success', '邀请链接已创建'))
      return true
    } catch (error) {
      console.error('[ERROR] [RoomInvite] Failed to create invite:', error)
      ElMessage.error(tf('room_settings.invite_create_failed', '创建失败'))
      return false
    } finally {
      isCreating.value = false
    }
  }

  /**
   * 确认撤销邀请链接弹窗
   *
   * @param inviteId 邀请链接 ID
   */
  const confirmRevoke = (inviteId: number) => {
    if (revokingId.value !== null) return
    showConfirmDialog({
      title: 'dialog.confirm',
      message: 'room_settings.invite_revoke_confirm',
      confirmText: 'common.confirm',
      cancelText: 'common.cancel',
      type: 'danger',
      onConfirm: () => {
        revokeInvite(inviteId).then(() => { })
      },
    })
  }

  /**
   * 执行撤销操作
   *
   * @param inviteId 邀请链接 ID
   */
  const revokeInvite = async (inviteId: number) => {
    const chatCode = currentRoom.value?.chatCode
    if (!chatCode) return

    revokingId.value = inviteId
    try {
      await revokeChatInviteApi(chatCode, inviteId)
      inviteList.value = inviteList.value.filter(invite => invite.id !== inviteId)
      ElMessage.success(tf('room_settings.invite_revoke_success', '邀请码已撤销'))
    } catch (error) {
      console.error('[ERROR] [RoomInvite] Failed to revoke invite:', error)
      ElMessage.error(tf('room_settings.invite_revoke_failed', '撤销失败'))
    } finally {
      revokingId.value = null
    }
  }

  /**
   * 复制邀请链接到剪贴板
   *
   * @param inviteCode 邀请码
   */
  const copyInviteUrl = async (inviteCode: string) => {
    if (!inviteCode) return
    try {
      await navigator.clipboard.writeText(buildInviteUrl(inviteCode))
      ElMessage.success(tf('common.copied', '已复制'))
    } catch {
      ElMessage.error(tf('common.copy_failed', '复制失败'))
    }
  }

  /**
   * 显示二维码弹窗
   * @param inviteCode 邀请码
   */
  const showQrCode = (inviteCode: string) => {
    if (!inviteCode) return
    qrDialogUrl.value = buildInviteUrl(inviteCode)
    qrDialogVisible.value = true
  }

  watch(roomSettingsDialogVisible, (visible) => {
    if (visible) {
      inviteList.value = []
      fetchInvites().then(() => { })
      resetForm()
    } else {
      resetForm()
    }
  }, { immediate: true })

  return {
    inviteList,
    inviteCount,
    inviteLimitTip,
    isLoading,
    isCreating,
    revokingId,
    selectedDate,
    selectedDateRadio,
    oneTimeLink,
    disabledDate,
    canCreate,
    formatDateTime,
    fetchInvites,
    createInvite,
    confirmRevoke,
    copyInviteUrl,
    resetForm,
    qrDialogVisible,
    qrDialogUrl,
    showQrCode,
  }
}
