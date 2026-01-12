<script setup lang="ts">
/**
 * 移动端访客加入页组件
 *
 * 功能：
 * - 两种加入方式切换（房间号+密码 / 邀请链接）
 * - 表单验证与凭证校验
 * - 校验成功后跳转到加入确认页
 *
 * 路由：/m/guest
 *
 * 依赖：
 * - useJoinInput: 加入房间输入逻辑
 * - MobileEntryShell: 布局壳组件
 */
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Search, ArrowLeft, Lock } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import MobileEntryShell from './MobileEntryShell.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { useJoinInput } from '@/composables/chat/join/useJoinInput'
import { useUserStore } from '@/stores/userStore'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

const {
  joinChatCredentialsForm,
  joinChatCredentialsFormRules,
  resetJoinForm,
  isValidating,
  validateAndGetPayload
} = useJoinInput()

const guestFormRef = ref<FormInstance>()

const handleGuestJoin = async () => {
  if (!guestFormRef.value) return
  const isValid = await guestFormRef.value.validate().catch(() => false)
  if (!isValid) return

  const payload = await validateAndGetPayload()
  if (!payload) return

  const chatCode = userStore.validatedChatRoom?.chatCode
  if (chatCode) {
    router.push(`/Join/${chatCode}`)
    return
  }
  ElMessage.error(t('api.retry_required'))
}

// Reset on mount (optional, but good practice)
// resetJoinForm() // useJoinInput might reset on its own or we might want to keep state. 
// IndexView resets on tab switch. Here we are a fresh view.
resetJoinForm()

watch(
  () => joinChatCredentialsForm.value.joinMode,
  () => {
    guestFormRef.value?.clearValidate()
  }
)
</script>

<template>
  <MobileEntryShell>
    <div class="view-header">
      <el-button link class="back-btn-icon" @click="router.back()">
        <el-icon :size="24"><ArrowLeft /></el-icon>
      </el-button>
      <h2>{{ t('mobile.index.tab_guest') }}</h2>
    </div>

    <div class="form-container">
      <el-form 
        ref="guestFormRef"
        :model="joinChatCredentialsForm"
        :rules="joinChatCredentialsFormRules"
        class="auth-form"
        label-position="top"
        @submit.prevent
      >
        <div class="join-mode-switch">
          <el-radio-group v-model="joinChatCredentialsForm.joinMode" size="small" class="pill-switch">
            <el-radio-button value="roomId/password">{{ t('chat.password_mode') }}</el-radio-button>
            <el-radio-button value="inviteUrl">{{ t('chat.invite_mode') }}</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="joinChatCredentialsForm.joinMode === 'roomId/password'" class="form-inputs">
          <el-form-item prop="chatCode" :label="t('guest.input_id')">
            <el-input 
              v-model="joinChatCredentialsForm.chatCode" 
              :placeholder="t('guest.input_id')" 
              size="large"
              :prefix-icon="Search"
              type="tel"
              maxlength="8"
              class="premium-input"
            />
          </el-form-item>
          <el-form-item prop="password" :label="t('auth.password')">
            <PasswordInput 
              v-model="joinChatCredentialsForm.password" 
              :placeholder="t('guest.input_pw')"
              class="premium-input"
              :prefix-icon="Lock"
            />
          </el-form-item>
        </div>

        <div v-else class="form-inputs">
          <el-form-item prop="inviteUrl" :label="t('guest.input_url')">
            <el-input 
              v-model="joinChatCredentialsForm.inviteUrl" 
              :placeholder="t('guest.input_url')" 
              type="textarea" 
              :rows="3" 
              resize="none"
              class="premium-input url-textarea"
            />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button 
            type="primary" 
            size="large" 
            class="submit-btn premium-btn" 
            :loading="isValidating"
            @click="handleGuestJoin"
          >
            {{ t('guest.join_btn') }}
          </el-button>
        </div>
      </el-form>
    </div>
  </MobileEntryShell>
</template>

<style scoped>
.view-header {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
}

.back-btn-icon {
  position: absolute;
  left: -8px;
  color: var(--text-main);
}

.view-header h2 {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: var(--text-main);
}

/* Reusing form styles matching IndexView logic, but scoping here */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-inputs {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.join-mode-switch {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}

/* Pill Switch */
.pill-switch :deep(.el-radio-button__inner) {
  border-radius: 20px;
  padding: 8px 20px;
  background: var(--tab-bg);
  border: none;
  font-size: 13px;
  margin: 0 4px;
  box-shadow: none;
}

.pill-switch :deep(.el-radio-button:first-child .el-radio-button__inner),
.pill-switch :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 20px;
}

.pill-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--primary);
  color: white;
  box-shadow: 0 4px 10px rgba(var(--primary-rgb), 0.3) !important;
}

/* Premium Input overrides */
:deep(.el-form-item__label) {
  color: var(--text-main);
  font-weight: 500;
  padding-bottom: 8px;
}

:deep(.premium-input .el-input__wrapper),
:deep(.premium-input .el-textarea__inner) {
  width: 100%;
  box-sizing: border-box;
  background: var(--input-bg);
  box-shadow: none !important;
  border: 1px solid transparent;
  border-radius: 16px;
  padding: 12px 16px;
  transition: all 0.3s ease;
}

:deep(.premium-input .el-input__wrapper.is-focus),
:deep(.premium-input .el-textarea__inner:focus) {
  background: var(--input-focus-bg);
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(var(--primary-rgb), 0.1) !important;
}

:deep(.premium-input .el-input__inner) {
  color: var(--text-main);
}

/* Buttons */
.premium-btn {
  height: 52px;
  border-radius: 16px;
  font-weight: 700;
  font-size: 16px;
  background: var(--primary-gradient);
  border: none;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.25);
}

.premium-btn:active {
  transform: scale(0.97);
}
</style>