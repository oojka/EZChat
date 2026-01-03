<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import {
  ArrowRight,
  Camera,
  CircleCheckFilled,
  CircleCloseFilled,
  Close,
  Edit,
  Picture,
  User
} from '@element-plus/icons-vue'
import { useRegister } from '@/hooks/useRegister.ts'
import { useI18n } from 'vue-i18n'
import useLogin from '@/hooks/useLogin.ts'
import PasswordInput from '@/components/PasswordInput.vue'
import { ElMessage } from 'element-plus'
import { useImageStore } from '@/stores/imageStore'

const { t } = useI18n()
const props = defineProps<{ active: boolean; flipped: boolean }>()
const emit = defineEmits<{ (e: 'flip'): void; (e: 'unflip'): void }>()

const { loginForm, loginFormRef, loginFormRules, isLoading, isLocked, secondsLeft, login } = useLogin()
const { registerForm, registerFormRules, registerFormRef, register, beforeAvatarUpload, handleAvatarSuccess, resetRegisterForm } = useRegister()
const imageStore = useImageStore()

const registerStep = ref<1 | 2 | 3 | 4>(1)
const isRegistering = ref(false)
const registrationResult = ref({ success: false, message: '' })
const defaultAvatarUrl = ref('') // 用于展示的默认头像 URL（不上传）

const progressPercentage = computed(() => {
  // 只有在步骤4且成功时才显示100%，否则根据当前步骤计算
  if (registerStep.value === 4 && registrationResult.value.success) {
    return 100
  }
  return (Math.min(registerStep.value, 3) / 3) * 100
})

// 页面加载时生成默认头像 URL（仅用于展示）
onMounted(() => {
  defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
})

const onFlip = () => {
  // 重置注册状态
  registerStep.value = 1
  registrationResult.value = { success: false, message: '' }
  resetRegisterForm()
  // 如果默认头像 URL 未生成，生成它（防止多次拉取）
  if (!defaultAvatarUrl.value) {
    defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
  }
  emit('flip')
}

const onUnflip = () => {
  emit('unflip')
  // 延迟重置，等待翻转动画完成
  setTimeout(() => {
    registerStep.value = 1
    registrationResult.value = { success: false, message: '' }
    resetRegisterForm()
    // 重置默认头像 URL（下次翻转时会重新生成）
    defaultAvatarUrl.value = ''
  }, 800)
}
const onAvatarSuccess = (response: any) => { handleAvatarSuccess(response) }

const validateStep = async (step: number) => {
  if (!registerFormRef.value) return false
  let fieldsToValidate: string[] = []
  if (step === 1) fieldsToValidate = [] // 头像不再需要验证
  else if (step === 2) fieldsToValidate = ['nickname', 'username']
  else if (step === 3) fieldsToValidate = ['password', 'confirmPassword']
  try {
    await registerFormRef.value.validateField(fieldsToValidate)
    return true
  } catch (error) {
    return false
  }
}

const nextStep = async () => {
  if (await validateStep(registerStep.value) && registerStep.value < 3) registerStep.value++
}
const prevStep = () => {
  if (registerStep.value > 1 && registerStep.value <= 3) registerStep.value--
}

const handleRegister = async () => {
  if (!await validateStep(3)) return
  isRegistering.value = true
  try {
    // 如果用户未设置头像（Image 对象为空或空串），上传默认头像
    if (!registerForm.value.avatar.objectUrl && !registerForm.value.avatar.objectThumbUrl) {
      registerForm.value.avatar = await imageStore.uploadDefaultAvatarIfNeeded(registerForm.value.avatar, 'user')
    }
    
    // 调用注册 API
    const success = await register()
    if (success) {
      // 注册成功：显示成功结果
      registrationResult.value = { success: true, message: t('auth.register_success_msg') }
      registerStep.value = 4
    } else {
      // 注册失败：保持在步骤3，不跳转到结果页
      registrationResult.value = { success: false, message: t('auth.register_fail_msg') }
      // 不设置 registerStep = 4，保持在步骤3
      ElMessage.error(t('auth.register_fail_msg'))
    }
  } catch (e: any) {
    // 捕获异常（头像上传失败、API 错误或网络错误）：保持在步骤3
    const errorMessage = e.message || t('common.error')
    registrationResult.value = { success: false, message: errorMessage }
    // 不设置 registerStep = 4，保持在步骤3
    ElMessage.error(errorMessage)
  } finally {
    isRegistering.value = false
  }
}
</script>

<template>
  <div class="flip-card-inner" :class="{ 'is-flipped': flipped }">
    <!-- 正面：登录 -->
    <div class="flip-card-front" :class="{ 'has-shadow': active }">
      <div class="login-header">
        <h2>{{ t('auth.login') }}</h2>
        <p class="login-subtitle">{{ t('auth.login_subtitle') }}</p>
      </div>

      <div class="login-content">
        <el-form :model="loginForm" :rules="loginFormRules" ref="loginFormRef" :show-message="false"
          :validate-on-rule-change="false" class="login-form" @submit.prevent>
          <el-form-item prop="username" class="username-input">
            <el-input v-model="loginForm.username" :placeholder="t('auth.username')" size="large">
              <template #prefix><el-icon>
                  <User />
                </el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password" class="password-input">
            <PasswordInput v-model="loginForm.password" :placeholder="t('auth.password')" @enter="login" />
          </el-form-item>
        </el-form>
      </div>

      <div class="login-footer">
        <el-button :loading="isLoading" :disabled="isLocked" type="primary" @click="login" class="login-btn"
          size="large">{{
            isLocked ? `${t('common.retry')} (${secondsLeft}s)` : t('auth.login') }}</el-button>
        <div class="signup-link-wrapper"><span class="signup-text">{{ t('auth.no_account') }}</span><a
            class="signup-btn" @click.stop="onFlip">「{{ t('auth.register') }}」</a></div>
      </div>
    </div>

    <!-- 背面：注册 -->
    <div class="flip-card-back" :class="{ 'has-shadow': active }" @click.stop>
      <div class="register-container">
        <el-button v-if="registerStep !== 4" class="close-flip-btn" :icon="Close" circle @click="onUnflip" />
        <div class="register-header">
          <div class="progress-section">
            <el-progress :percentage="progressPercentage" :show-text="false" :stroke-width="4"
              :status="registerStep === 4 ? (registrationResult.success ? 'success' : 'exception') : ''"
              class="custom-progress" />
            <div class="step-label">{{ registerStep === 4 ? 'COMPLETED' : `Step ${registerStep} / 3` }}</div>
          </div>
          <h4>{{ registerStep === 4 ? t('auth.register_result') : t('auth.register') }}</h4>
        </div>
        <el-form :model="registerForm" label-position="top" :rules="registerFormRules" ref="registerFormRef"
          class="register-form-content" hide-required-asterisk>
          <transition name="el-fade-in-linear" mode="out-in">
            <div v-if="registerStep === 1" key="step1" class="step-container avatar-step">
              <div class="avatar-upload-box shifted-down">
                <el-upload class="avatar-uploader-large" action="/api/auth/register/upload" :show-file-list="false"
                  :on-success="onAvatarSuccess" :before-upload="beforeAvatarUpload">
                  <div v-if="registerForm.avatar.objectThumbUrl" class="avatar-preview-lg"><img
                      :src="registerForm.avatar.objectThumbUrl" class="avatar-img" />
                    <div class="edit-mask-lg"><el-icon>
                        <Camera />
                      </el-icon><span>{{ t('common.change') }}</span></div>
                  </div>
                  <div v-else-if="defaultAvatarUrl" class="avatar-preview-lg"><img
                      :src="defaultAvatarUrl" class="avatar-img" />
                    <div class="edit-mask-lg"><el-icon>
                        <Camera />
                      </el-icon><span>{{ t('common.change') }}</span></div>
                  </div>
                  <div v-else class="placeholder-square-lg"><el-icon size="40">
                      <Picture />
                    </el-icon><span>{{ t('auth.select_image') }}</span></div>
                </el-upload>
                <div class="avatar-info-area">
                  <p class="step-hint">{{ t('auth.avatar_hint') }}</p>
                </div>
                <el-form-item prop="avatar" class="hidden-item" :show-message="false" />
              </div>
            </div>
            <div v-else-if="registerStep === 2" key="step2" class="step-container">
              <div class="form-vertical-stack">
                <el-form-item :label="t('auth.nickname')" prop="nickname" class="nickname-input"><el-input
                    v-model="registerForm.nickname" :placeholder="t('auth.nickname_placeholder')" size="large"
                    :prefix-icon="Edit" @keydown.enter.prevent="nextStep" /></el-form-item>
                <el-form-item :label="t('auth.username')" prop="username" class="username-input" ><el-input v-model="registerForm.username"
                    :placeholder="t('auth.username_placeholder')" size="large" :prefix-icon="User"
                    @keydown.enter.prevent="nextStep" /></el-form-item>
              </div>
            </div>
            <div v-else-if="registerStep === 3" key="step3" class="step-container">
              <div class="form-vertical-stack">
                <el-form-item :label="t('auth.password')" prop="password" class="password-input">
                  <PasswordInput v-model="registerForm.password" :placeholder="t('auth.password_hint')"
                    @enter="handleRegister" />
                </el-form-item>
                <el-form-item :label="t('auth.confirm_password')" prop="confirmPassword" class="confirm-password-input">
                  <PasswordInput v-model="registerForm.confirmPassword"
                    :placeholder="t('auth.confirm_password_placeholder')" @enter="handleRegister" />
                </el-form-item>
              </div>
            </div>
            <div v-else key="step4" class="step-container result-step">
              <div class="result-content">
                <el-icon :class="['result-icon', registrationResult.success ? 'success' : 'error']">
                  <CircleCheckFilled v-if="registrationResult.success" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <h3 class="result-title">{{ registrationResult.success ? t('auth.register_success') :
                  t('auth.register_fail') }}</h3>
                <p class="result-message">{{ registrationResult.message }}</p>
              </div>
            </div>
          </transition>
          <div class="register-actions">
            <template v-if="registerStep === 1"><el-button type="primary" @click="nextStep" class="step-btn-full">{{
              t('common.next') }} <el-icon class="el-icon--right">
                  <ArrowRight />
                </el-icon></el-button></template>
            <template v-else-if="registerStep === 2"><el-button @click="prevStep" class="step-btn-half">{{
              t('common.back') }}</el-button><el-button type="primary" @click="nextStep" class="step-btn-half">{{
                  t('common.next') }}</el-button></template>
            <template v-else-if="registerStep === 3"><el-button @click="prevStep" class="step-btn-half"
                :disabled="isRegistering">{{ t('common.back') }}</el-button><el-button type="primary"
                @click="handleRegister" class="step-btn-half" :loading="isRegistering">{{ t('auth.register_btn')
                }}</el-button></template>
            <template v-else><el-button v-if="!registrationResult.success" @click="registerStep = 1"
                class="step-btn-half">{{ t('common.retry') }}</el-button><el-button type="primary" @click="onUnflip"
                :class="registrationResult.success ? 'step-btn-full' : 'step-btn-half'">{{ t('auth.login_now')
                }}</el-button></template>
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
  padding: 32px;
  box-sizing: border-box;
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--border-glass);
  overflow: hidden;
  transform: translateZ(0);
  transition: background-color 0.3s ease, box-shadow 0.3s ease;
  display: flex;
  flex-direction: column;
}

html.dark .flip-card-front,
html.dark .flip-card-back {
  background: var(--bg-card);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.has-shadow {
  box-shadow: var(--shadow-glass);
}

html.dark .has-shadow {
  box-shadow: 0 20px 80px rgba(0, 0, 0, 0.8);
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
  padding: 24px 32px 24px;
}

.login-header {
  margin-bottom: 32px;
  text-align: center;
}

.login-header h2 {
  font-size: 28px;
  margin: 0 0 8px;
  font-weight: 800;
  color: var(--text-900);
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-500);
  font-weight: 500;
}

.login-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
}

.login-form {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset;
  background-color: var(--bg-page);
  padding: 6px 16px;
  transition: all 0.3s;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

.login-form .username-input {
  margin-bottom: 28px;
}

.login-footer {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 20px;
  margin-top: auto;
  border-top: 1px solid var(--border-glass);
}

.login-btn {
  width: 100%;
  border-radius: var(--radius-md);
  font-weight: 800;
  font-size: 16px;
  height: 44px;
  background: var(--primary);
  border: none;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.25);
  transition: all 0.3s;
}

.login-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.35);
}

.signup-link-wrapper {
  text-align: center;
  font-size: 14px;
}

.signup-text {
  color: var(--text-500);
}

.signup-btn {
  color: var(--primary);
  font-weight: 700;
  cursor: pointer;
  margin-left: 4px;
  font-size: 16px;
  transition: all 0.2s;
  text-decoration: none;
}

.register-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.close-flip-btn {
  position: absolute;
  right: -16px;
  top: -12px;
  z-index: 10;
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.close-flip-btn:hover {
  background: var(--el-border-color-light);
  color: var(--text-900);
  transform: rotate(90deg);
}

.register-header {
  text-align: center;
  margin-bottom: 16px;
}

.progress-section {
  margin-bottom: 12px;
  padding: 0 40px;
}

:deep(.custom-progress .el-progress-bar__outer) {
  background-color: var(--el-border-color-extra-light) !important;
}

.step-label {
  font-size: 10px;
  font-weight: 800;
  color: var(--primary);
  letter-spacing: 1px;
}

.register-header h4 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-900);
}

.register-form-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.step-container {
  height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.shifted-down {
  transform: translateY(10px);
}

.avatar-upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.avatar-info-area {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.step-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-400);
  margin: 0;
}

.avatar-error-container {
  height: 20px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.avatar-error-text {
  font-size: 11px;
  color: var(--el-color-danger);
  font-weight: 600;
  text-align: center;
}

.form-vertical-stack {
  display: column;
  flex-direction: column;
  justify-content: center;
}

.form-vertical-stack .password-input {
  margin-top: 32px !important;
}

.form-vertical-stack .confirm-password-input {
  margin-top: 40px !important;
}

.form-vertical-stack .nickname-input {
  margin-top: 32px !important;
}

.form-vertical-stack .username-input {
  margin-top: 40px !important;
}


.spaced-item {
  margin-bottom: 24px !important;
}

:deep(.el-form-item) {
  margin-bottom: 12px;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-700);
  padding-bottom: 4px !important;
  line-height: 1 !important;
}

.avatar-uploader-large {
  text-align: center;
  display: flex;
  justify-content: center;
}

.avatar-preview-lg,
.placeholder-square-lg {
  width: 150px;
  height: 150px;
  border-radius: calc(150px * var(--avatar-border-radius-ratio)); /* 45px (30%) */
  overflow: hidden;
  position: relative;
  cursor: pointer;
  margin: 0 auto;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  background: var(--bg-page);
}

.placeholder-square-lg {
  border: 2px dashed var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  gap: 8px;
}

.placeholder-square-lg span {
  font-size: 12px;
  font-weight: 700;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-mask-lg {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: 0.3s;
  font-size: 12px;
}

.avatar-preview-lg:hover .edit-mask-lg {
  opacity: 1;
}

.hidden-item {
  margin: 0 !important;
  height: 0;
  overflow: hidden;
}

.result-step {
  text-align: center;
}

.result-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.result-icon {
  font-size: 64px;
}

.result-icon.success {
  color: var(--el-color-success);
}

.result-icon.error {
  color: var(--el-color-danger);
}

.result-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--text-900);
}

.result-message {
  margin: 0;
  font-size: 14px;
  color: var(--text-500);
  line-height: 1.6;
}

.register-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 12px;
}

.step-btn-full {
  width: 100%;
  height: 44px;
  border-radius: var(--radius-base);
  font-weight: 800;
  font-size: 14px;
}

.step-btn-half {
  flex: 1;
  height: 44px;
  border-radius: var(--radius-base);
  font-weight: 800;
  font-size: 14px;
}

.el-button--default.step-btn-half {
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  transition: all 0.3s;
}

.el-button--default.step-btn-half:hover {
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

/* 密码查看图标样式 */
.pwd-view-icon {
  cursor: pointer;
  color: var(--text-400);
  transition: color 0.2s;
  font-size: 18px;
  padding: 4px;
}

.pwd-view-icon:hover {
  color: var(--primary);
}

/* 禁止密码选取 */
.password-input-wrapper :deep(.el-input__inner) {
  user-select: none !important;
  -webkit-user-select: none !important;
  -moz-user-select: none !important;
  -ms-user-select: none !important;
}
</style>
