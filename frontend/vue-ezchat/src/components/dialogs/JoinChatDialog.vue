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
import { useJoinInput } from '@/hooks/chat/join/useJoinInput.ts'
import { useLoginJoin } from '@/hooks/chat/join/useLoginJoin.ts'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { useI18n } from 'vue-i18n'

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
    const payload = await validateAndGetPayload(false)
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
        targetCode = (payload as any).chatCode
      }

      await navigateToChat(targetCode || '')
    } else {
      // FAILED
      joinResult.value = { success: false, message: t('chat.join_failed') || 'Join failed' }
      joinStep.value = 2
    }

  } catch (e: any) {
    // 这里的 error 通常已经被 hook 内部捕获并显示 ElMessage，但为了保险
    console.error(e)
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
      <div class="dialog-header-actions">
        <button v-if="joinStep === 1" class="close-btn" type="button" @click="closeJoinDialog">
          <el-icon>
            <Close />
          </el-icon>
        </button>
      </div>
      <div v-if="joinStep === 1" class="dialog-title-area">
        <h3>{{ t('chat.join_chat') }}</h3>
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
                <el-input v-model="joinChatCredentialsForm.password" :placeholder="t('chat.password_placeholder')"
                  type="password" size="large" show-password @keydown.enter.prevent="handleJoin" />
              </el-form-item>
            </template>

            <template v-else>
              <el-form-item :label="t('chat.invite_url')" prop="inviteUrl">
                <el-input v-model="joinChatCredentialsForm.inviteUrl" :placeholder="t('chat.invite_url_placeholder')"
                  type="textarea" :rows="3" resize="none" size="large" @keydown.enter.prevent="handleJoin" />
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
            <el-icon :class="['result-icon', joinResult.success ? 'success' : 'error']">
              <CircleCheckFilled v-if="joinResult.success" />
              <CircleCloseFilled v-else />
            </el-icon>

            <h3 class="result-title">
              {{ joinResult.success ? t('chat.join_success') : t('chat.join_failed') }}
            </h3>

            <p class="result-message">{{ joinResult.message }}</p>
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
}

html.dark :deep(.ez-modern-dialog) {
  background: var(--bg-card) !important;
  backdrop-filter: blur(24px) saturate(200%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(200%) !important;
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
  padding: 10px 32px 24px;
  display: flex;
  flex-direction: column;
  min-height: 320px;
  /* 压缩高度，使其更紧凑 */
}

/* --- Header Actions --- */
.dialog-header-actions {
  position: relative;
  /* 为 absolute close-btn 提供定位锚点 */
  height: 0;
}

.close-btn {
  position: absolute;
  right: 16px;
  top: 24px;
  /* 调整顶部距离 */
  z-index: 10;
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s;
}

.close-btn:hover {
  background: var(--el-border-color-light);
  color: var(--text-900);
  transform: rotate(90deg);
}

.dialog-title-area {
  text-align: center;
  margin-top: 20px;
  margin-bottom: 24px;
  padding: 0 40px;
}

.dialog-title-area h3 {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-900);
  margin: 0;
}

/* --- Steps --- */
.step-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* --- Mode Switcher --- */
.mode-toggle-pill {
  display: flex;
  background: var(--bg-page);
  padding: 4px;
  border-radius: 14px;
  margin-bottom: 24px;
  border: 1px solid var(--el-border-color-light);
}

.mode-tab {
  flex: 1;
  height: 36px;
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
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

html.dark .mode-tab.active {
  background: var(--el-bg-color-overlay);
}

/* --- Form --- */
.join-form {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-700);
  padding-bottom: 6px !important;
  line-height: 1 !important;
}

/* Input Styles */
:deep(.el-input__wrapper),
:deep(.el-textarea__inner) {
  background-color: var(--bg-page) !important;
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  border-radius: var(--radius-base);
  transition: all 0.3s;
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
  padding-top: 20px;
  height: 100%;
}

.result-content {
  text-align: center;
  margin-bottom: 32px;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
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
  margin: 0 0 8px;
}

.result-message {
  font-size: 14px;
  color: var(--text-500);
  margin: 0;
  max-width: 80%;
  line-height: 1.5;
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
