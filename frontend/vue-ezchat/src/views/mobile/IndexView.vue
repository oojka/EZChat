<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { User, ArrowRight, Search, Check, Upload } from '@element-plus/icons-vue'
import type { FormInstance, UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import { uploadAvatarApi } from '@/api/Auth'

// Composables
import { useKeyboardVisible } from '@/composables/useKeyboardVisible'
import useLogin from '@/composables/useLogin'
import { useRegister } from '@/composables/useRegister'
import { useJoinInput } from '@/composables/chat/join/useJoinInput'
import { useUserStore } from '@/stores/userStore'

// Components
import AppLogo from '@/components/AppLogo.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import Avatar from '@/components/Avatar.vue'

const { t } = useI18n()
const router = useRouter()
const { isKeyboardVisible } = useKeyboardVisible()
const userStore = useUserStore()

type ActiveTab = 'guest' | 'login' | 'register'
const activeTab = ref<ActiveTab>('guest')

// ==================== Guest Logic ====================
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

// ==================== Login Logic ====================
const { 
  loginForm, 
  isLoading: isLoginLoading, 
  login 
} = useLogin()

const loginFormRef = ref<FormInstance>()

const handleLogin = async () => {
  if (!loginFormRef.value) return
  // useLogin's login() handles validation internally but we can pre-validate UI
  // actually useLogin login() does validation checks.
  await login()
}

// ==================== Register Logic ====================
const { 
  registerForm, 
  register, 
  registerFormRules, 
  registerFormRef, 
  beforeAvatarUpload, 
  handleAvatarSuccess,
  resetRegisterForm
} = useRegister()

const registerStep = ref(1)
const isRegisterLoading = ref(false)

const nextRegisterStep = async () => {
  if (!registerFormRef.value) return
  
  // Validate Step 1 fields
  try {
    await registerFormRef.value.validateField(['nickname', 'username'])
    registerStep.value = 2
  } catch (e) {
    // Validation failed
    return
  }
}

const prevRegisterStep = () => {
  registerStep.value = 1
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  isRegisterLoading.value = true
  try {
    const success = await register()
    if (success) {
      ElMessage.success(t('auth.register_success_msg'))
      // Switch to login tab
      activeTab.value = 'login'
      loginForm.username = registerForm.value.username
      loginForm.password = '' // Don't auto-fill password for security/UX
      // Reset register state
      registerStep.value = 1
      resetRegisterForm()
    }
  } catch (e) {
    // Error handled in useRegister or global handler
  } finally {
    isRegisterLoading.value = false
  }
}

const createUploadError = (message: string) => ({
  name: 'UploadError',
  message,
  status: 0,
  method: 'POST',
  url: '/auth/register/upload'
})

const customUploadRequest = async (options: UploadRequestOptions) => {
  const { file, onSuccess, onError } = options
  try {
    const uploadFile = file instanceof File ? file : new File([file], 'avatar')
    const res = await uploadAvatarApi(uploadFile)
    if (res.status === 1 && res.data) {
      onSuccess(res)
      handleAvatarSuccess(res)
      return
    }
    onError(createUploadError(res.message || t('api.image_upload_failed')))
  } catch (err) {
    const message = err instanceof Error ? err.message : t('api.image_upload_failed')
    onError(createUploadError(message))
  }
}

// Reset forms on tab switch
watch(activeTab, (newTab) => {
  if (newTab === 'guest') resetJoinForm()
  // login form reset is manual if needed, but keeping input might be nice
  if (newTab !== 'register') registerStep.value = 1
})

watch(
  () => joinChatCredentialsForm.value.joinMode,
  () => {
    guestFormRef.value?.clearValidate()
  }
)

</script>

<template>
  <div class="mobile-index-view">
    <!-- Animated Background -->
    <div class="ambient-bg">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="noise-overlay"></div>
    </div>

    <!-- Header -->
    <div class="index-header" :class="{ 'is-hidden': isKeyboardVisible }">
      <div class="logo-area">
        <div class="logo-glow"></div>
        <AppLogo :size="56" />
      </div>
      <div class="title-area">
        <h1 class="app-name">EZ Chat</h1>
        <p class="app-slogan">{{ t('index.subtitle') }}</p>
      </div>
    </div>

    <!-- Main Card -->
    <div class="auth-card glass-panel" :class="{ 'expanded': isKeyboardVisible }">
      <!-- Tabs -->
      <div class="tabs-wrapper">
        <el-radio-group v-model="activeTab" class="mobile-auth-tabs premium-tabs">
          <el-radio-button value="guest">{{ t('mobile.index.tab_guest') }}</el-radio-button>
          <el-radio-button value="login">{{ t('mobile.index.tab_login') }}</el-radio-button>
          <el-radio-button value="register">{{ t('mobile.index.tab_register') }}</el-radio-button>
        </el-radio-group>
      </div>

      <!-- Content -->
      <div class="content-wrapper">
        <Transition name="fade-scale" mode="out-in">
          
          <!-- GUEST TAB -->
          <div v-if="activeTab === 'guest'" key="guest" class="tab-pane">
            <div class="tab-header animate-enter">
              <h2>{{ t('mobile.index.quick_join') }}</h2>
              <p>{{ t('mobile.index.join_hint') }}</p>
            </div>
            
            <el-form 
              ref="guestFormRef"
              :model="joinChatCredentialsForm"
              :rules="joinChatCredentialsFormRules"
              class="auth-form"
              @submit.prevent
            >
              <div class="join-mode-switch animate-enter delay-1">
                <el-radio-group v-model="joinChatCredentialsForm.joinMode" size="small" class="pill-switch">
                  <el-radio-button value="roomId/password">{{ t('chat.password_mode') }}</el-radio-button>
                  <el-radio-button value="inviteUrl">{{ t('chat.invite_mode') }}</el-radio-button>
                </el-radio-group>
              </div>

              <div v-if="joinChatCredentialsForm.joinMode === 'roomId/password'" class="form-inputs animate-enter delay-2">
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
                  />
                </el-form-item>
              </div>

              <div v-else class="form-inputs animate-enter delay-2">
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

              <div class="form-actions animate-enter delay-3">
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

          <!-- LOGIN TAB -->
          <div v-else-if="activeTab === 'login'" key="login" class="tab-pane">
            <div class="tab-header animate-enter">
              <h2>{{ t('auth.login') }}</h2>
              <p>{{ t('auth.login_subtitle') }}</p>
            </div>

            <el-form 
              ref="loginFormRef"
              :model="loginForm"
              class="auth-form"
              @submit.prevent
            >
              <div class="form-inputs animate-enter delay-1">
                <el-form-item :label="t('auth.username')">
                  <el-input 
                    v-model="loginForm.username" 
                    :placeholder="t('auth.username')" 
                    size="large"
                    :prefix-icon="User"
                    class="premium-input"
                  />
                </el-form-item>
                <el-form-item :label="t('auth.password')">
                  <PasswordInput 
                    v-model="loginForm.password" 
                    :placeholder="t('auth.password')"
                    class="premium-input"
                  />
                </el-form-item>
              </div>

              <div class="form-actions animate-enter delay-2">
                <el-button 
                  type="primary" 
                  size="large" 
                  class="submit-btn premium-btn" 
                  :loading="isLoginLoading"
                  @click="handleLogin"
                >
                  {{ t('auth.login') }}
                </el-button>
              </div>
            </el-form>
          </div>

          <!-- REGISTER TAB -->
          <div v-else-if="activeTab === 'register'" key="register" class="tab-pane">
            <div class="tab-header animate-enter">
              <h2>{{ t('auth.register') }}</h2>
              <div class="step-indicator">
                <div class="step-dot" :class="{ active: registerStep >= 1 }"></div>
                <div class="step-line" :class="{ active: registerStep >= 2 }"></div>
                <div class="step-dot" :class="{ active: registerStep >= 2 }"></div>
              </div>
            </div>

            <el-form 
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerFormRules"
              class="auth-form"
              @submit.prevent
            >
              <!-- STEP 1 -->
              <div v-if="registerStep === 1" class="step-content animate-enter delay-1">
                <div class="avatar-section">
                   <el-upload
                    class="avatar-uploader"
                    :show-file-list="false"
                    :http-request="customUploadRequest"
                    :before-upload="beforeAvatarUpload"
                    accept="image/*"
                  >
                    <div class="avatar-wrapper premium-avatar">
                      <Avatar 
                        v-if="registerForm.avatar.imageUrl" 
                        :url="registerForm.avatar.imageUrl" 
                        :size="80" 
                        class="register-avatar"
                      />
                      <div v-else class="avatar-placeholder">
                        <el-icon :size="24"><Upload /></el-icon>
                      </div>
                      <div class="avatar-badge">
                        <el-icon><Check v-if="registerForm.avatar.imageUrl" /><Upload v-else /></el-icon>
                      </div>
                    </div>
                  </el-upload>
                  <span class="avatar-hint">{{ t('auth.avatar_upload_hint') }}</span>
                </div>

                <el-form-item prop="nickname" :label="t('auth.nickname')">
                  <el-input 
                    v-model="registerForm.nickname" 
                    :placeholder="t('auth.nickname_placeholder')" 
                    size="large"
                    class="premium-input"
                  />
                </el-form-item>
                
                <el-form-item prop="username" :label="t('auth.username')">
                  <el-input 
                    v-model="registerForm.username" 
                    :placeholder="t('auth.username_placeholder')" 
                    size="large"
                    class="premium-input"
                  />
                </el-form-item>

                <div class="form-actions">
                  <el-button type="primary" size="large" class="submit-btn premium-btn" @click="nextRegisterStep">
                    {{ t('common.next') }} <el-icon class="el-icon--right"><ArrowRight /></el-icon>
                  </el-button>
                </div>
              </div>

              <!-- STEP 2 -->
              <div v-else class="step-content animate-enter delay-1">
                <div class="user-summary premium-card">
                  <Avatar :url="registerForm.avatar.imageUrl || ''" :size="48" />
                  <div class="user-info">
                    <span class="nickname">{{ registerForm.nickname }}</span>
                    <span class="username">@{{ registerForm.username }}</span>
                  </div>
                </div>

                <el-form-item prop="password" :label="t('auth.password')">
                  <PasswordInput 
                    v-model="registerForm.password" 
                    :placeholder="t('auth.password')"
                    class="premium-input"
                  />
                </el-form-item>

                <el-form-item prop="confirmPassword" :label="t('auth.confirm_password')">
                  <PasswordInput 
                    v-model="registerForm.confirmPassword" 
                    :placeholder="t('auth.confirm_password')"
                    class="premium-input"
                  />
                </el-form-item>

                <div class="form-actions">
                  <el-button 
                    type="primary" 
                    size="large" 
                    class="submit-btn premium-btn" 
                    :loading="isRegisterLoading"
                    @click="handleRegister"
                  >
                    {{ t('auth.register_btn') }}
                  </el-button>
                  <el-button text class="back-btn" @click="prevRegisterStep">
                    {{ t('common.back') }}
                  </el-button>
                </div>
              </div>
            </el-form>
          </div>

        </Transition>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ================= CSS VARIABLES ================= */
.mobile-index-view {
  --primary-rgb: 59, 130, 246;
  --glass-bg: rgba(255, 255, 255, 0.75);
  --glass-border: rgba(255, 255, 255, 0.5);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.05);
  --primary-gradient: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
  --text-main: #1f2937;
  --text-sub: #6b7280;
  --input-bg: rgba(255, 255, 255, 0.6);
  --input-focus-bg: #ffffff;
  --tab-bg: rgba(0, 0, 0, 0.05);
  --tab-active-bg: #ffffff;
  --tab-active-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  --orb-1: #c7d2fe;
  --orb-2: #fbcfe8;
  --orb-3: #e9d5ff;
}

html.dark .mobile-index-view {
  --glass-bg: rgba(20, 20, 25, 0.65);
  --glass-border: rgba(255, 255, 255, 0.08);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  --primary-gradient: linear-gradient(135deg, var(--primary) 0%, #4338ca 100%);
  --text-main: #f3f4f6;
  --text-sub: #9ca3af;
  --input-bg: rgba(0, 0, 0, 0.2);
  --input-focus-bg: rgba(0, 0, 0, 0.4);
  --tab-bg: rgba(255, 255, 255, 0.05);
  --tab-active-bg: rgba(255, 255, 255, 0.1);
  --tab-active-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  --orb-1: #1e1b4b;
  --orb-2: #312e81;
  --orb-3: #4c1d95;
}

/* ================= LAYOUT ================= */
.mobile-index-view {
  height: var(--app-height);
  padding: var(--safe-area-top) 16px var(--safe-area-bottom);
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  overflow: hidden;
  position: relative;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* ================= AMBIENT BACKGROUND ================= */
.ambient-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
  animation: float 20s infinite ease-in-out;
}

.orb-1 {
  top: -10%;
  right: -10%;
  width: 80vw;
  height: 80vw;
  background: var(--orb-1);
  animation-delay: 0s;
}

.orb-2 {
  bottom: 20%;
  left: -20%;
  width: 60vw;
  height: 60vw;
  background: var(--orb-2);
  animation-delay: -5s;
}

.orb-3 {
  top: 40%;
  right: -20%;
  width: 50vw;
  height: 50vw;
  background: var(--orb-3);
  animation-delay: -10s;
}

.noise-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)' opacity='0.04'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 1;
}

@keyframes float {
  0% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
  100% { transform: translate(0, 0) scale(1); }
}

/* ================= HEADER ================= */
.index-header {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0 32px;
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 2;
  position: relative;
}

.index-header.is-hidden {
  padding: 10px 0;
  opacity: 0;
  height: 0;
  margin: 0;
  transform: translateY(-20px);
  pointer-events: none;
}

.logo-area {
  position: relative;
  margin-bottom: 20px;
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 120%;
  height: 120%;
  background: var(--primary);
  filter: blur(30px);
  opacity: 0.3;
  border-radius: 50%;
}

.title-area {
  text-align: center;
}

.app-name {
  font-size: 36px;
  font-weight: 800;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
  line-height: 1.1;
  letter-spacing: -1px;
}

.app-slogan {
  font-size: 15px;
  color: var(--text-sub);
  margin: 8px 0 0;
  font-weight: 500;
  letter-spacing: 0.5px;
}

/* ================= GLASS CARD ================= */
.auth-card {
  flex: 1;
  border-radius: 32px 32px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 2;
  margin-top: auto;
}

.glass-panel {
  background: var(--glass-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
}

.auth-card.expanded {
  border-radius: 20px 20px 0 0;
}

/* ================= TABS ================= */
.tabs-wrapper {
  padding: 24px 24px 0;
}

.premium-tabs {
  background: var(--tab-bg);
  padding: 4px;
  border-radius: 16px;
  border: 1px solid rgba(255,255,255,0.05);
}

:deep(.premium-tabs .el-radio-button) {
  flex: 1;
}

:deep(.premium-tabs .el-radio-button__inner) {
  width: 100%;
  min-height: 40px;
  background: transparent;
  border: none;
  border-radius: 12px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-sub);
  box-shadow: none !important;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

:deep(.premium-tabs .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--tab-active-bg);
  color: var(--primary);
  box-shadow: var(--tab-active-shadow) !important;
  transform: scale(0.98);
}

/* ================= CONTENT ================= */
.content-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 32px 24px;
}

.tab-pane {
  display: flex;
  flex-direction: column;
}

.tab-header {
  margin-bottom: 32px;
  text-align: center;
}

.tab-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 8px;
  letter-spacing: -0.5px;
}

.tab-header p {
  font-size: 14px;
  color: var(--text-sub);
  margin: 0;
  line-height: 1.5;
}

/* ================= FORMS ================= */
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

:deep(.el-form-item) {
  margin-bottom: 0;
}

:deep(.el-form-item__label) {
  color: var(--text-main);
  font-weight: 500;
  font-size: 14px;
  padding-bottom: 8px;
  line-height: 1.2;
}

/* Premium Input Styles */
:deep(.premium-input .el-input__wrapper),
:deep(.premium-input .el-textarea__inner) {
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
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(var(--primary-rgb), 0.1) !important;
}

:deep(.premium-input .el-input__inner) {
  font-size: 16px;
  color: var(--text-main);
  height: 24px;
}

:deep(.url-textarea) {
  border-radius: 16px;
  background: transparent;
}

/* Join Switch Pill */
.join-mode-switch {
  display: flex;
  justify-content: center;
}

.pill-switch :deep(.el-radio-button__inner) {
  border-radius: 20px;
  padding: 8px 20px;
  background: var(--tab-bg);
  border: none;
  font-size: 13px;
  margin: 0 4px;
}

.pill-switch :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 20px;
}
.pill-switch :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 20px;
}

.pill-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--primary);
  color: white;
  box-shadow: 0 4px 10px rgba(var(--primary-rgb), 0.3) !important;
}

/* ================= BUTTONS ================= */
.form-actions {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.premium-btn {
  width: 100%;
  height: 52px;
  border-radius: 16px;
  font-weight: 700;
  font-size: 16px;
  background: var(--primary-gradient);
  border: none;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.25);
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.premium-btn:active {
  transform: scale(0.97);
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.2);
}

.back-btn {
  width: 100%;
  font-weight: 500;
  color: var(--text-sub);
}

.back-btn:hover {
  color: var(--text-main);
}

/* ================= REGISTER SPECIFIC ================= */
.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 16px;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--glass-border);
  transition: all 0.4s ease;
}

.step-line {
  width: 40px;
  height: 3px;
  background: var(--glass-border);
  border-radius: 2px;
  transition: all 0.4s ease;
}

.step-indicator .active {
  background: var(--primary);
  box-shadow: 0 0 10px rgba(var(--primary-rgb), 0.4);
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.premium-avatar {
  position: relative;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
  background: var(--bg-page);
}

.premium-avatar:active {
  transform: scale(0.92);
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--input-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-sub);
  border: 2px dashed var(--glass-border);
}

.avatar-badge {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: var(--primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  border: 3px solid var(--bg-card);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.avatar-hint {
  font-size: 13px;
  color: var(--text-sub);
  font-weight: 500;
}

.user-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: 20px;
  background: var(--input-bg);
  margin-bottom: 8px;
  border: 1px solid var(--glass-border);
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nickname {
  font-weight: 700;
  color: var(--text-main);
  font-size: 16px;
}

.username {
  font-size: 13px;
  color: var(--text-sub);
}

/* ================= ANIMATIONS ================= */
.animate-enter {
  animation: slideUpFade 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.delay-1 { animation-delay: 0.1s; }
.delay-2 { animation-delay: 0.2s; }
.delay-3 { animation-delay: 0.3s; }

@keyframes slideUpFade {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Transition Group */
.fade-scale-enter-active,
.fade-scale-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.fade-scale-enter-from {
  opacity: 0;
  transform: scale(0.96) translateY(10px);
}

.fade-scale-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(-10px);
}
</style>
