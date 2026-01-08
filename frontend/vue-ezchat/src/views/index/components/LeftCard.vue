<script setup lang="ts">
import { ref, watch } from 'vue'
import { ChatLineRound, Connection, Right, Search, Ticket, Close } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useJoinInput } from '@/hooks/chat/join/useJoinInput.ts'
import { useUserStore } from '@/stores/userStore'
import { useI18n } from 'vue-i18n'
import PasswordInput from '@/components/PasswordInput.vue'
import type { FormInstance } from 'element-plus'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const props = defineProps<{ active: boolean; flipped: boolean }>()
const emit = defineEmits<{ (e: 'flip'): void; (e: 'unflip'): void }>()

// 1. 实例化模块
const inputModule = useJoinInput()

// 3. 解构所需状态和方法
const {
  joinChatCredentialsForm,
  joinChatCredentialsFormRules,
  resetJoinForm,
  isValidating,
  validateAndGetPayload
} = inputModule

/**
 * 处理验证并跳转到加入页面
 * 替代原有的 handleValidate
 */
const handleValidateAndRedirect = async () => {
  const isValid = await joinFormRef.value?.validate()
  if (!isValid) return false
  const payload = await validateAndGetPayload()
  // 验证成功，userStore.validatedChatRoom 已更新
  // 直接跳转到加入页面
  // 注意：这里需要获取 validatedChatRoom 中的 chatCode
  const chatCode = userStore.validatedChatRoom?.chatCode
  if (chatCode) {
    router.push(`/Join/${chatCode}`)

  }
}



// 表单 ref，用于清除校验
const joinFormRef = ref<FormInstance>()

// 翻面时重置表单和校验
const onFlip = () => {
  resetJoinForm()
  joinFormRef.value?.clearValidate()
  emit('flip')
}

// 取消翻面时重置表单和校验
const onUnflip = () => {
  emit('unflip')
  setTimeout(() => {
    resetJoinForm()
    joinFormRef.value?.clearValidate()
  }, 800)
}

// 监听模式切换，清除校验
watch(() => joinChatCredentialsForm.value.joinMode, () => {
  joinFormRef.value?.clearValidate()
})
</script>

<template>
  <div class="flip-card-inner" :class="{ 'is-flipped': flipped }">
    <!-- 正面 -->
    <div class="flip-card-front">
      <div class="guest-content">
        <div class="guest-header">
          <span class="guest-badge">QUICK ACCESS</span>
          <h3 class="guest-title">{{ t('guest.title') }}</h3>
        </div>
        <div class="guest-features">
          <div class="feature-item"><el-icon>
              <Connection />
            </el-icon><span>{{ t('guest.feature_id') }}</span></div>
          <div class="feature-item"><el-icon>
              <ChatLineRound />
            </el-icon><span>{{ t('guest.feature_no_reg') }}</span></div>
        </div>
        <div class="guest-footer">
          <div class="guest-arrow" @click.stop="onFlip"><span>{{ t('guest.join_btn') }}</span><el-icon>
              <Right />
            </el-icon></div>
        </div>
      </div>
      <div class="guest-icon-wrapper"><el-icon class="huge-icon">
          <Ticket />
        </el-icon></div>
    </div>

    <!-- 背面 -->
    <div class="flip-card-back" @click.stop>
      <div class="join-form-container">
        <button class="ez-close-btn close-flip-btn" type="button" @click="onUnflip">
          <el-icon>
            <Close />
          </el-icon>
        </button>
        <div class="form-header">
          <h4>{{ t('guest.join_title') }}</h4>
        </div>
        <el-form ref="joinFormRef" :model="joinChatCredentialsForm" @submit.prevent class="join-form"
          :rules="joinChatCredentialsFormRules">
          <div class="tab-section">
            <el-radio-group v-model="joinChatCredentialsForm.joinMode" class="modern-tabs-small" size="small">
              <el-radio-button value="roomId/password">ID</el-radio-button>
              <el-radio-button value="inviteUrl">URL</el-radio-button>
            </el-radio-group>
          </div>
          <div class="input-section">
            <div v-if="joinChatCredentialsForm.joinMode === 'roomId/password'" class="input-group id-mode">
              <el-form-item class="mb-0" prop="chatCode">
                <el-input v-model="joinChatCredentialsForm.chatCode" :placeholder="t('guest.input_id')" size="large"
                  :prefix-icon="Search" @keyup.enter="handleValidateAndRedirect" />
              </el-form-item>
              <el-form-item class="mb-0" prop="password">
                <PasswordInput v-model="joinChatCredentialsForm.password" :placeholder="t('guest.input_pw')"
                  @keyup.enter="handleValidateAndRedirect" />
              </el-form-item>
            </div>
            <div v-else class="input-group link-mode">
              <el-form-item class="mb-0" prop="inviteUrl">
                <el-input v-model="joinChatCredentialsForm.inviteUrl" :placeholder="t('guest.input_url')" size="large"
                  :prefix-icon="Connection" type="textarea" :rows="4" resize="none" class="url-textarea"
                  @keyup.enter="handleValidateAndRedirect" />
              </el-form-item>
            </div>
          </div>
          <div class="form-actions">
            <el-button type="primary" @click="handleValidateAndRedirect" class="join-submit-btn" :loading="isValidating"
              :disabled="(!joinChatCredentialsForm.chatCode && !joinChatCredentialsForm.inviteUrl) || isValidating">{{
                t('guest.join_submit') }}</el-button>
            <el-button @click="onUnflip" class="join-cancel-btn" :disabled="isValidating">{{ t('common.cancel')
              }}</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flip-card-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  transform-style: preserve-3d;
}

.flip-card-inner.is-flipped {
  transform: rotateY(180deg);
}

.flip-card-front,
.flip-card-back {
  position: absolute;
  width: 100%;
  height: 100%;
  backface-visibility: hidden;
  border-radius: var(--radius-xl);
  padding: 40px;
  box-sizing: border-box;
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--border-glass);
  box-shadow: var(--shadow-glass);
  overflow: hidden;
  transform: translateZ(0);
  transition: background-color 0.3s ease, box-shadow 0.3s ease;
}

html.dark .flip-card-front,
html.dark .flip-card-back {
  background: var(--bg-card);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.is-flipped .flip-card-front {
  visibility: hidden;
  transition: visibility 0s 0.4s;
}

.flip-card-inner:not(.is-flipped) .flip-card-front {
  visibility: visible;
  transition: visibility 0s 0.4s;
}

.flip-card-back {
  transform: rotateY(180deg);
}

.guest-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 2;
  position: relative;
}

.guest-header {
  margin-bottom: 32px;
}

.guest-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 800;
  color: var(--primary);
  background: rgba(64, 158, 255, 0.12);
  padding: 6px 12px;
  border-radius: var(--radius-round);
  margin-bottom: 16px;
  letter-spacing: 1.5px;
  transition: all 0.3s ease;
}

html.dark .guest-badge {
  background: rgba(77, 171, 255, 0.1);
  color: #4dabff;
}

.guest-title {
  font-size: 36px;
  margin: 0;
  color: var(--text-900);
  line-height: 1.1;
  font-weight: 900;
}

.guest-features {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 32px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-700);
  font-weight: 600;
  font-size: 15px;
}

.feature-item .el-icon {
  font-size: 20px;
  color: var(--primary);
  background: var(--bg-card);
  padding: 8px;
  border-radius: var(--radius-round);
  border: 1px solid var(--el-border-color-light);
}

.guest-footer {
  display: flex;
  justify-content: flex-end;
}

.guest-arrow {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  background: var(--primary);
  padding: 12px 24px;
  border-radius: var(--radius-md);
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 8px 16px rgba(64, 158, 255, 0.3);
  transition: all 0.3s ease;
  cursor: pointer;
}

.guest-arrow:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.4);
}

.guest-icon-wrapper {
  position: absolute;
  right: -60px;
  bottom: -40px;
  color: var(--primary);
  opacity: 0.06;
  transform: rotate(-15deg);
  pointer-events: none;
  z-index: 1;
}

.huge-icon {
  font-size: 280px;
}

.close-flip-btn {
  position: absolute;
  right: -16px;
  top: -12px;
  z-index: 10;
}

.join-form-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 0;
}

.form-header {
  text-align: center;
  margin-bottom: 12px;
}

.form-header h4 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--text-900);
}

.join-form {
  display: flex;
  flex-direction: column;
}

.tab-section {
  margin-bottom: 26px;
}

.modern-tabs-small {
  width: 100%;
  display: flex;
  background: var(--bg-page);
  padding: 4px;
  border-radius: var(--radius-base);
  border: 1px solid var(--el-border-color-extra-light);
}

:deep(.modern-tabs-small .el-radio-button) {
  flex: 1;
}

:deep(.modern-tabs-small .el-radio-button__inner) {
  width: 100%;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  padding: 8px 0;
  font-weight: 700;
  box-shadow: none !important;
  color: var(--text-500);
  transition: all 0.3s;
}

:deep(.modern-tabs-small .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--bg-card);
  color: var(--primary);
  box-shadow: var(--shadow-glass) !important;
}

.input-section {
  height: 120px;
  display: flex;
  flex-direction: column;
}

.input-group {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.id-mode {
  justify-content: center;
  gap: 10px;
}

.link-mode {
  justify-content: center;
}

.url-textarea :deep(.el-textarea__inner) {
  padding: 10px 16px;
  line-height: 1.4;
  background-color: var(--bg-page);
  color: var(--text-900);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-base);
  transition: all 0.3s;
}

.url-textarea :deep(.el-textarea__inner:focus) {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

.form-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 17px;
}

.join-submit-btn {
  width: 100%;
  height: 46px;
  border-radius: var(--radius-md);
  font-weight: 800;
  font-size: 16px;
  margin: 0 !important;
}

.join-cancel-btn {
  width: 100%;
  height: 46px;
  border-radius: var(--radius-md);
  font-weight: 700;
  font-size: 15px;
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  margin: 0 !important;
  transition: all 0.3s;
}

.join-cancel-btn:hover {
  background: var(--el-border-color-light);
  color: var(--text-700);
}

:deep(.el-input__wrapper) {
  background-color: var(--bg-page) !important;
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  border-radius: var(--radius-base);
  transition: all 0.3s;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

:deep(.el-input__inner) {
  color: var(--text-900) !important;
}
</style>
