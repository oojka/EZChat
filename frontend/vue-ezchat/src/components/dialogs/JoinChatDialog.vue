<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ArrowRight,
  Link,
  Lock,
  Close,
  CircleCheckFilled,
  CircleCloseFilled,
} from '@element-plus/icons-vue'
import { useJoinChat } from '@/hooks/chat/join/useJoinChat.ts'
import { useRoomStore } from '@/stores/roomStore'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const roomStore = useRoomStore()
const { joinChatDialogVisible } = storeToRefs(roomStore)

// 使用 Hook 提供的业务逻辑
const {
  joinChatCredentialsForm,
  joinChatCredentialsFormRules,
  joinChatCredentialsFormRef,
  isLoading,
  isRoomIdPasswordMode,
  isInviteUrlMode,
  joinMode,
  joinStep,
  joinResult,
  closeJoinDialog,
  handleJoin,
  handleResultConfirm,
  changeJoinMode,
} = useJoinChat()



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
              {{ joinResult.success ? (t('chat.join_success') || '加入成功') : (t('chat.join_failed') || '加入失败') }}
            </h3>

            <p class="result-message">{{ joinResult.message }}</p>
          </div>

          <div class="dialog-actions">
            <el-button :type="joinResult.success ? 'primary' : 'default'" class="action-btn-full" size="large"
              @click="handleResultConfirm">
              {{ joinResult.success ? (t('create_chat.enter_now') || '立即进入') : (t('common.retry') || '重试') }}
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
  padding: 10px 32px 32px;
  display: flex;
  flex-direction: column;
  min-height: 420px;
  /* 与 CreateChatDialog 保持一致 */
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
