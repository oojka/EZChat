/**
 * 房间成员管理 Composable
 *
 * 核心职责：
 * - 管理成员选择状态（踢出、转让）
 * - 执行踢出成员操作（含确认弹窗）
 * - 执行房主转让操作（含确认弹窗）
 * - 自动同步选择状态与成员列表
 *
 * 使用示例：
 * ```vue
 * const { members, selectedUids, kickSelected, transferOwner } = useRoomMemberManagement()
 * ```
 *
 * @module useRoomMemberManagement
 */
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { kickChatMembersApi, transferChatOwnerApi } from '@/api/Chat'
import { showConfirmDialog } from '@/components/dialogs/confirmDialog'
import type { ChatMember } from '@/type'

/**
 * 房间成员管理业务逻辑 Hook
 *
 * @returns 成员列表、选择状态、踢出/转让方法等
 */
export const useRoomMemberManagement = () => {
  const { t } = useI18n()
  const roomStore = useRoomStore()
  const { currentRoom, currentRoomCode, roomSettingsDialogVisible } = storeToRefs(roomStore)

  const selectedUids = ref<string[]>([])
  const transferTargetUid = ref('')
  const isKicking = ref(false)
  const isTransferring = ref(false)

  const members = computed<ChatMember[]>(() => currentRoom.value?.chatMembers || [])
  const ownerUid = computed(() => currentRoom.value?.ownerUid || '')

  const transferCandidates = computed(() =>
    members.value.filter((m) => m.uid !== ownerUid.value)
  )

  const syncSelections = () => {
    const validUids = new Set(members.value.map((m) => m.uid))
    selectedUids.value = selectedUids.value.filter((uid) => validUids.has(uid) && uid !== ownerUid.value)
    if (transferTargetUid.value && !validUids.has(transferTargetUid.value)) {
      transferTargetUid.value = ''
    }
    if (transferTargetUid.value === ownerUid.value) {
      transferTargetUid.value = ''
    }
  }

  watch([members, ownerUid], syncSelections)

  watch(roomSettingsDialogVisible, async (visible) => {
    if (!visible) return
    if (!currentRoomCode.value) return
    await roomStore.fetchRoomMembers(currentRoomCode.value)
  })

  const kickSelected = () => {
    if (!currentRoom.value) return
    const targets = selectedUids.value.filter((uid) => uid && uid !== ownerUid.value)
    if (!targets.length) {
      ElMessage.warning(t('room_settings.member_kick_empty') || 'Please select members')
      return
    }

    showConfirmDialog({
      title: 'dialog.confirm',
      message: t('room_settings.member_kick_confirm', [targets.length]),
      type: 'danger',
      onConfirm: async () => {
        isKicking.value = true
        try {
          const res = await kickChatMembersApi(currentRoom.value!.chatCode, { memberUids: targets })
          if (res && res.status === 1) {
            ElMessage.success(t('room_settings.member_kick_success') || 'Removed')
            selectedUids.value = []
            await roomStore.fetchRoomMembers(currentRoom.value!.chatCode)
            return
          }
          ElMessage.error(res?.message || t('room_settings.member_kick_failed') || 'Remove failed')
        } catch (error) {
          console.error('[ERROR] [RoomMembers] Kick failed:', error)
          ElMessage.error(t('room_settings.member_kick_failed') || 'Remove failed')
        } finally {
          isKicking.value = false
        }
      }
    })
  }

  const transferOwner = (targetUid?: string) => {
    if (!currentRoom.value) return
    const resolvedUid = targetUid || transferTargetUid.value
    if (!resolvedUid) {
      ElMessage.warning(t('room_settings.member_transfer_empty') || 'Select a new owner')
      return
    }

    const target = transferCandidates.value.find((m) => m.uid === resolvedUid)
    const targetName = target?.nickname || resolvedUid

    showConfirmDialog({
      title: 'dialog.confirm',
      message: t('room_settings.member_transfer_confirm', [targetName]),
      type: 'warning',
      onConfirm: async () => {
        isTransferring.value = true
        try {
          const res = await transferChatOwnerApi(currentRoom.value!.chatCode, { newOwnerUid: resolvedUid })
          if (res && res.status === 1) {
            ElMessage.success(t('room_settings.member_transfer_success') || 'Transferred')
            roomStore.updateRoomInfo({
              ...currentRoom.value!,
              ownerUid: resolvedUid,
            })
            transferTargetUid.value = ''
            return
          }
          ElMessage.error(res?.message || t('room_settings.member_transfer_failed') || 'Transfer failed')
        } catch (error) {
          console.error('[ERROR] [RoomMembers] Transfer failed:', error)
          ElMessage.error(t('room_settings.member_transfer_failed') || 'Transfer failed')
        } finally {
          isTransferring.value = false
        }
      }
    })
  }

  const kickMember = (uid: string) => {
    if (!currentRoom.value) return
    if (!uid || uid === ownerUid.value) return
    showConfirmDialog({
      title: 'dialog.confirm',
      message: t('room_settings.member_kick_confirm', [1]),
      type: 'danger',
      onConfirm: async () => {
        isKicking.value = true
        try {
          const res = await kickChatMembersApi(currentRoom.value!.chatCode, { memberUids: [uid] })
          if (res && res.status === 1) {
            ElMessage.success(t('room_settings.member_kick_success') || 'Removed')
            selectedUids.value = selectedUids.value.filter((id) => id !== uid)
            await roomStore.fetchRoomMembers(currentRoom.value!.chatCode)
            return
          }
          ElMessage.error(res?.message || t('room_settings.member_kick_failed') || 'Remove failed')
        } catch (error) {
          console.error('[ERROR] [RoomMembers] Kick failed:', error)
          ElMessage.error(t('room_settings.member_kick_failed') || 'Remove failed')
        } finally {
          isKicking.value = false
        }
      }
    })
  }

  return {
    members,
    ownerUid,
    selectedUids,
    transferTargetUid,
    transferCandidates,
    isKicking,
    isTransferring,
    kickSelected,
    kickMember,
    transferOwner,
  }
}
