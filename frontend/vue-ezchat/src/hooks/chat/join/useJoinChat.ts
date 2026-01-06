/**
 * useJoinChat.ts
 * * 职责：
 * 聚合管理加入聊天室的所有逻辑，作为一个统一的入口 (Facade)。
 * * 重构说明 (Refactor Note):
 * 逻辑已拆分至 `composables/chat/` 目录下的：
 * 1. useJoinForm.ts (表单与验证)
 * 2. useJoinAction.ts (加入动作与头像)
 * 3. useJoinDialogController.ts (UI控制)
 * 本文件作为 Facade 保持向后兼容，并协调各模块交互。
 */

import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { isInviteJoinReq, isPasswordJoinReq } from '@/utils/validators.ts'

// API & Stores
import { useUserStore } from '@/stores/userStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'

// Types
import type { JoinChatReq } from '@/type'

// Composables
import { useJoinForm } from '@/hooks/chat/join/useJoinForm.ts'
import { useJoinAction } from '@/hooks/chat/join/useJoinAction.ts'
import { useJoinDialogController } from '@/hooks/chat/join/useJoinDialogController.ts'

export const useJoinChat = () => {
  const { t } = useI18n()

  const userStore = useUserStore()
  const appStore = useAppStore()
  const roomStore = useRoomStore()

  // === Compose Modules (组合模块) ===
  const formModule = useJoinForm()
  const actionModule = useJoinAction()
  const dialogModule = useJoinDialogController()

  // #region Orchestration Logic (编排逻辑)

  /**
   * 场景C：正式用户ChatView内加入
   * 1. 验证表单并获取 validatedChatRoom (复用 handleValidate)
   * 2. 构建加入请求 (复用逻辑)
   * 3. 调用加入 API
   * 4. 设置结果并切换到步骤2
   */
  const handleDialogJoinChat = async (): Promise<boolean> => {
    try {
      // Step 1: 验证
      // 调用 Form 模块的 handleValidate (不跳转)
      const isValid = await formModule.handleValidate(false)
      if (!isValid) return false

      // 继续后续流程（需要 loading）
      return await appStore.runWithLoading(
        t('common.processing') || 'Processing...',
        async () => {
          // Step 2: 再次确认验证信息存在 (防御性编程)
          if (!userStore.validatedChatRoom || !userStore.validateChatJoinReq) {
            throw createAppError(
              ErrorType.VALIDATION,
              'Validation info lost, please retry',
              {
                severity: ErrorSeverity.ERROR,
                component: 'useJoinChat',
                action: 'handleDialogJoinChat'
              }
            )
          }

          // Step 3: 构建加入请求
          const validatedReq = userStore.validateChatJoinReq
          let req: JoinChatReq

          if (isInviteJoinReq(validatedReq)) {
            // 邀请码模式
            req = { inviteCode: validatedReq.inviteCode }
          } else if (isPasswordJoinReq(validatedReq)) {
            // 密码模式
            req = { chatCode: validatedReq.chatCode, password: validatedReq.password }
          } else {
            throw createAppError(ErrorType.VALIDATION, 'Invalid request type', { severity: ErrorSeverity.ERROR, component: 'useJoinChat', action: 'handleDialogJoinChat' })
          }

          // Step 4: 调用加入 API
          await roomStore.joinChat(req)

          // Step 5: 设置成功结果 (UI控制)
          dialogModule.joinResult.value = {
            success: true,
            message: t('chat.join_success') || 'Joined room successfully'
          }
          dialogModule.joinStep.value = 2

          return true
        }
      )
    } catch (e: unknown) {
      // 统一错误处理
      const errorMessage = isAppError(e)
        ? e.message
        : e instanceof Error
          ? e.message
          : t('common.error') || 'Operation failed'

      dialogModule.joinResult.value = {
        success: false,
        message: errorMessage
      }
      dialogModule.joinStep.value = 2
      return false
    }
  }

  /** 别名：处理对话框提交 (Enter键支持) */
  const handleDialogSubmit = handleDialogJoinChat

  /**
   * 处理结果确认
   * 包装 DialogController 的方法，传入当前表单的 chatCode 作为兜底
   */
  const handleResultConfirmFacade = () => {
    const currentCode = formModule.joinChatCredentialsForm.value.chatCode
    return dialogModule.handleResultConfirm(currentCode)
  }

  // #endregion

  // #region Watchers (监听器)

  // 监听弹窗打开/关闭，实现自动重置表单
  watch(() => roomStore.joinChatDialogVisible, (newVal) => {
    if (newVal) {
      // 打开时立即清空表单
      formModule.resetJoinForm()
    } else {
      // 关闭时延迟重置（避免动画闪烁）
      setTimeout(() => {
        formModule.resetJoinForm()
      }, 300)
    }
  })

  // #endregion

  return {
    // === State (状态) - From Form ===
    joinChatCredentialsForm: formModule.joinChatCredentialsForm,
    joinChatCredentialsFormRef: formModule.joinChatCredentialsFormRef,
    isValidating: formModule.isValidating,
    isRoomIdPasswordMode: formModule.isRoomIdPasswordMode,
    isInviteUrlMode: formModule.isInviteUrlMode,
    joinMode: formModule.joinMode,
    joinChatCredentialsFormRules: formModule.joinChatCredentialsFormRules,

    // === State (状态) - From Action ===
    guestNickname: actionModule.guestNickname,
    guestAvatar: actionModule.guestAvatar,
    defaultAvatarUrl: actionModule.defaultAvatarUrl,
    isLoading: actionModule.isLoading,

    // === State (状态) - From Dialog ===
    joinStep: dialogModule.joinStep,
    joinResult: dialogModule.joinResult,

    // === Actions (动作) - From Form ===
    handleValidate: formModule.handleValidate,
    resetJoinForm: formModule.resetJoinForm,
    toggleJoinMode: formModule.toggleJoinMode,
    changeJoinMode: formModule.changeJoinMode,

    // === Actions (动作) - From Action ===
    handleGuestJoin: actionModule.handleGuestJoin,
    handleLoginJoin: actionModule.handleLoginJoin,
    initDefaultAvatarUrl: actionModule.initDefaultAvatarUrl,
    resetGuestForm: actionModule.resetGuestForm,
    handleAvatarSuccess: actionModule.handleAvatarSuccess,

    // === Actions (动作) - From Dialog ===
    closeJoinDialog: dialogModule.closeJoinDialog,

    // === Actions (动作) - Orchestrated ===
    handleResultConfirm: handleResultConfirmFacade,
    handleDialogJoinChat,
    handleDialogSubmit,
    handleJoin: handleDialogJoinChat,
  }
}
