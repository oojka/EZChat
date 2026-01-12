<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  ArrowRight,
  Link,
  Lock,
  Close,
  CircleCheckFilled,
  CircleCloseFilled,
} from '@element-plus/icons-vue'
import { useJoinInput } from '@/composables/chat/join/useJoinInput.ts'
import { useLoginJoin } from '@/composables/chat/join/useLoginJoin.ts'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useI18n } from 'vue-i18n'
import { isAppError } from '@/error/ErrorTypes'
import PasswordInput from '@/components/PasswordInput.vue'

const { t } = useI18n()
const router = useRouter()
const roomStore = useRoomStore()
const userStore = useUserStore()
const { joinChatDialogVisible } = storeToRefs(roomStore)

// 1. 实例化基础模块
const inputModule = useJoinInput()
const loginJoinModule = useLoginJoin()

// 3. 解构所需状态和方法 (直接从各模块获取)
const {
  joinChatCredentialsForm,
  joinChatCredentialsFormRules,
  joinChatCredentialsFormRef,
  isRoomIdPasswordMode,
  isInviteUrlMode,
  changeJoinMode,
  resetJoinForm,
  validateAndGetPayload,
} = inputModule

const {
  isLoading,
  executeJoin,
} = loginJoinModule

// #region UI State (原 useJoinDialogController 逻辑)

/** 对话框步骤状态 (1: 表单, 2: 结果) */
const joinStep = ref<1 | 2>(1)

/** 加入结果状态 */
const joinResult = ref<{ success: boolean; message: string }>({
  success: false,
  message: ''
})

// #endregion

// #region UI Methods

/** 关闭对话框并重置状态 */
const closeJoinDialog = () => {
  roomStore.joinChatDialogVisible = false
  setTimeout(() => {
    // 延迟重置，避免动画突变
    joinStep.value = 1
    joinResult.value = { success: false, message: '' }
  }, 300)
}

/** 跳转到聊天室 */
const navigateToChat = async (chatCode: string) => {
  if (chatCode) {
    await router.push(`/chat/${chatCode}`)
  } else {
    await router.push('/chat')
  }
  closeJoinDialog()
}

/**
 * 处理加入动作
 */
const handleJoin = async () => {
  try {
    // 1. 验证并获取 Payload
    const payload = await validateAndGetPayload(joinChatCredentialsForm.value, false)
    if (!payload) return // 验证失败或取消

    // 2. 执行加入
    const result = await executeJoin(payload, false)

    // 3. 处理结果
    if (result === 'SUCCESS') {
      joinResult.value = { success: true, message: t('chat.join_success') || 'Joined successfully' }
      joinStep.value = 2
    } else if (result === 'ALREADY_JOINED') {
      // 已加入：直接跳转，不显示成功页
      // 优先使用 validatedChatRoom，如果为空则尝试从 payload (roomId模式) 获取
      let targetCode = userStore.validatedChatRoom?.chatCode

      if (!targetCode && payload && 'chatCode' in payload) {
        targetCode = (payload as { chatCode: string }).chatCode
      }

      await navigateToChat(targetCode || '')
    } else {
      // FAILED (General)
      joinResult.value = { success: false, message: t('chat.join_failed') || 'Join failed' }
      joinStep.value = 2
    }

  } catch (e: unknown) {
    // 捕获验证或加入过程中的错误，显示在 Dialog 的结果页中
    let errMsg = t('chat.join_failed')

    if (e) {
      if (typeof e === 'string') {
        errMsg = e
      } else if (e instanceof Error) {
        errMsg = e.message
      } else if (isAppError(e)) {
        errMsg = e.message
      } else if (typeof e === 'object' && e !== null && 'message' in e) {
        // 兜底：尝试读取任意对象的 message 属性
        errMsg = String((e as { message: unknown }).message)
      }
    }

    // 尝试翻译错误消息（如果是 api.xxx 格式）
    if (errMsg && errMsg.startsWith('api.')) {
      errMsg = t(errMsg)
    }

    joinResult.value = { success: false, message: errMsg }
    joinStep.value = 2
  }
}

/**
 * 处理结果确认
 * 成功：跳转到聊天室并关闭对话框
 * 失败：返回步骤1重新填写
 */
const handleResultConfirm = async () => {
  if (joinResult.value.success) {
    // 成功：跳转到聊天室
    // 优先使用 validatedChatRoom 中的 chatCode
    let targetCode = userStore.validatedChatRoom?.chatCode

    // 如果没有，且当前表单中有 chatCode (roomId 模式)，则使用表单值
    if (!targetCode && joinChatCredentialsForm.value.chatCode) {
      targetCode = joinChatCredentialsForm.value.chatCode
    }

    await navigateToChat(targetCode || '')
  } else {
    // 失败：返回步骤1
    joinStep.value = 1
  }
}

// 监听弹窗打开/关闭，实现自动重置表单
watch(() => roomStore.joinChatDialogVisible, (newVal) => {
  if (newVal) {
    resetJoinForm()
    joinStep.value = 1 // 重置步骤
  }
})

// #endregion

</script>

<template>
  <el-dialog :model-value="joinChatDialogVisible" @update:model-value="closeJoinDialog" width="480px"
    class="ez-modern-dialog join-dialog-modern" align-center destroy-on-close :show-close="false"
    :close-on-click-modal="false">
    <template #header>
      <div class="join-header">
        <div class="ez-dialog-header-actions">
          <button v-if="joinStep === 1" class="ez-close-btn" type="button" @click="closeJoinDialog">
            <el-icon>
              <Close />
            </el-icon>
          </button>
        </div>
        <!-- Keep title area in DOM to preserve height, but hide text in result step -->
        <div class="dialog-title-area" :style="{ visibility: joinStep === 1 ? 'visible' : 'hidden' }">
          <h3>{{ t('chat.join_chat') }}</h3>
        </div>
      </div>
    </template>

    <div class="join-dialog-content">
      <transition name="el-fade-in-linear" mode="out-in">
        <div v-if="joinStep === 1" key="step1" class="step-container">

          <div class="mode-toggle-pill">
            <div :class="['mode-tab', { active: isRoomIdPasswordMode }]" @click="changeJoinMode('roomId/password')">
              <el-icon>
                <Lock />
              </el-icon>
              <span>{{ t('chat.password_mode') }}</span>
            </div>
            <div :class="['mode-tab', { active: isInviteUrlMode }]" @click="changeJoinMode('inviteUrl')">
              <el-icon>
                <Link />
              </el-icon>
              <span>{{ t('chat.invite_mode') }}</span>
            </div>
          </div>

          <el-form :model="joinChatCredentialsForm" :rules="joinChatCredentialsFormRules"
            :ref="joinChatCredentialsFormRef" label-position="top" class="join-form" hide-required-asterisk
            @submit.prevent="handleJoin">
            <template v-if="isRoomIdPasswordMode">
              <el-form-item :label="t('chat.chat_code')" prop="chatCode">
                <el-input v-model="joinChatCredentialsForm.chatCode" :placeholder="t('chat.chat_code_placeholder')"
                  size="large" maxlength="8" @keydown.enter.prevent="handleJoin" />
              </el-form-item>

              <el-form-item :label="t('chat.password')" prop="password">
                <PasswordInput v-model="joinChatCredentialsForm.password" :placeholder="t('chat.password_placeholder')"
                  size="large" @enter="handleJoin" />
              </el-form-item>
            </template>

            <template v-else>
              <el-form-item :label="t('chat.invite_url')" style="margin-top: 25px;" prop="inviteUrl">
                <el-input v-model="joinChatCredentialsForm.inviteUrl" :placeholder="t('chat.invite_url_placeholder')"
                  type="textarea" :rows="5" resize="none" size="large" @keydown.enter.prevent="handleJoin" />
              </el-form-item>
            </template>
          </el-form>

          <div class="dialog-actions">
            <el-button type="primary" class="action-btn-full" size="large" :loading="isLoading" @click="handleJoin">
              {{ t('chat.join') }}
              <el-icon class="el-icon--right">
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>

        <div v-else key="step2" class="step-container result-container">
          <div class="result-content">
            <div class="result-summary">
              <el-icon :class="['result-icon', joinResult.success ? 'success' : 'error']">
                <CircleCheckFilled v-if="joinResult.success" />
                <CircleCloseFilled v-else />
              </el-icon>

              <h3 class="result-title">
                {{ joinResult.success ? t('chat.join_success') : t('chat.join_failed') }}
              </h3>

              <p class="result-message">{{ joinResult.message }}</p>
            </div>
          </div>

          <div class="dialog-actions">
            <el-button :type="joinResult.success ? 'primary' : 'default'" class="action-btn-full" size="large"
              @click="handleResultConfirm">
              {{ joinResult.success ? t('create_chat.enter_now') : t('common.retry') }}
            </el-button>
          </div>
        </div>
      </transition>
    </div>
  </el-dialog>
</template>

<style scoped>
/* --- Dialog Container --- */
:deep(.ez-modern-dialog) {
  background: var(--bg-glass) !important;
  backdrop-filter: var(--blur-glass) !important;
  -webkit-backdrop-filter: var(--blur-glass) !important;
  border: 1px solid var(--border-glass) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-glass) !important;
  overflow: hidden;
  transition: all 0.3s var(--ease-out-expo);
  /* Ensure dialog dimensions match CreateChatDialog style reference */
  width: 480px !important;
}

html.dark :deep(.ez-modern-dialog) {
  background: var(--bg-card) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

/* 重置 Element Plus header 默认样式 */
.join-dialog-modern :deep(.el-dialog__header) {
  padding: 0 !important;
  margin: 0 !important;
}

.join-dialog-modern :deep(.el-dialog__body) {
  padding: 0 !important;
}

/* 布局容器 */
.join-dialog-content {
  position: relative;
  /* header 与内容区域之间增加间距（padding-top），保持呼吸感 */
  padding: 0 32px 20px;
  display: flex;
  flex-direction: column;
  /* 固定高度，防止切换时的抖动，压缩整体高度 */
  height: 320px;
  overflow: hidden;
}


/* --- Header Actions --- */
.join-header {
  position: relative;
  text-align: center;
  /* 顶部 20px，保持统一 */
  padding-top: 16px;
}

.join-header :deep(.ez-dialog-header-actions) {
  /* 覆盖全局位置: 顶部 (20px + 2px offset) = 22px center -> top approx 6px */
  top: 12px;
  right: 12px;
}


.dialog-title-area {
  text-align: center;
  margin-bottom: 4px;
  padding: 0 40px;
}

.dialog-title-area h3 {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-900);
  margin: 0;
}

/* --- Transitions --- */
.step-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

/* --- Mode Switcher --- */
.mode-toggle-pill {
  display: flex;
  background: var(--el-fill-color-light);
  /* Softer than bg-page in dark mode */
  padding: 4px;
  border-radius: 14px;
  /* Reduced margin */
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
}

html.dark .mode-toggle-pill {
  background: var(--bg-input);
  border-color: var(--el-border-color-darker);
}

.mode-tab {
  flex: 1;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-500);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s var(--ease-out-expo);
}

.mode-tab:hover {
  color: var(--text-700);
}

.mode-tab.active {
  background: var(--bg-card);
  color: var(--primary);
  box-shadow: var(--shadow-glass);
}

/* Remove html.dark override to ensure it uses var(--bg-card) which is #1e1f20 in dark mode, matching LeftCard */

/* --- Form --- */
.join-form {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.el-form-item) {
  /* Reduced margin */
  margin-bottom: 12px;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-700);
  padding-bottom: 4px !important;
  line-height: 1 !important;
}

/* Input Styles - Standard Inputs */
:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  /* Ensure bg is page color for contrast against glass dialog */
  /* background-color handled globally */
  border-radius: var(--radius-base);
  transition: all 0.3s;
}

/* Force standard height for non-textarea inputs to match design system (48px) if 'size=large' isn't catching vertically or needs override */
:deep(.el-input--large .el-input__wrapper) {
  height: 44px !important;
}

:deep(.el-input__wrapper.is-focus),
:deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  color: var(--text-900) !important;
}

/* --- Result Step --- */
.result-container {
  justify-content: center;
  align-items: center;
  padding-top: 0;
  padding-bottom: 0;
  height: 100%;
}

.result-content {
  text-align: center;
  margin-bottom: 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  width: 100%;
}

.result-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  width: 100%;
}

.result-icon {
  font-size: 64px;
  margin-bottom: 16px;
  transition: all 0.3s;
}

.result-icon.success {
  color: var(--el-color-success);
}

.result-icon.error {
  color: var(--el-color-danger);
}

.result-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-900);
  margin: 0 0 12px;
}

.result-message {
  font-size: 14px;
  color: var(--text-500);
  margin-top: 8px;
  /* Widen message area to prevent wrapping */
  max-width: 100%;
  padding: 0 16px;
  line-height: 1.6;
  word-break: break-word;
}

/* --- Bottom Actions --- */
.dialog-actions {
  margin-top: auto;
  padding-top: 16px;
}

.action-btn-full {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 800;
  border-radius: 14px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

.action-btn-full:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
}
</style>
