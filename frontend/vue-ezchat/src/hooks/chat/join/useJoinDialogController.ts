import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useUserStore } from '@/stores/userStore.ts'
import type { JoinChatCredentialsForm } from '@/type'

/**
 * useJoinDialogController
 * 职责：管理加入聊天室对话框的 UI 状态和流程控制
 */
export const useJoinDialogController = () => {
    const router = useRouter()
    const roomStore = useRoomStore()
    const userStore = useUserStore()

    // #region State

    /** 对话框步骤状态 (1: 表单, 2: 结果) */
    const joinStep = ref<1 | 2>(1)

    /** 加入结果状态 */
    const joinResult = ref<{ success: boolean; message: string }>({
        success: false,
        message: ''
    })

    // #endregion

    // #region Methods

    /** 关闭对话框并重置状态 */
    const closeJoinDialog = () => {
        roomStore.joinChatDialogVisible = false
        // 重置步骤和结果
        joinStep.value = 1
        joinResult.value = { success: false, message: '' }
    }

    /**
     * 处理结果确认
     * 成功：跳转到聊天室并关闭对话框
     * 失败：返回步骤1重新填写
     */
    const handleResultConfirm = async (currentFormChatCode?: string) => {
        if (joinResult.value.success) {
            // 成功：跳转到聊天室
            // 优先使用 validatedChatRoom 中的 chatCode，因为邀请码模式下表单可能没有 chatCode
            // 如果 validatedChatRoom 也没有，则尝试使用传入的当前表单 chatCode
            const targetCode = userStore.validatedChatRoom?.chatCode || currentFormChatCode

            if (targetCode) {
                await router.push(`/chat/${targetCode}`)
            } else {
                // 兜底跳转
                await router.push('/chat')
            }
            closeJoinDialog()
        } else {
            // 失败：返回步骤1
            joinStep.value = 1
        }
    }

    // #endregion

    return {
        joinStep,
        joinResult,
        closeJoinDialog,
        handleResultConfirm
    }
}
