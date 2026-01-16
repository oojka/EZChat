<script setup lang="ts">
/**
 * 移动端注册页组件
 *
 * 功能：
 * - 两步注册向导
 *   - Step 1: 头像上传 + 昵称 + 用户名
 *   - Step 2: 密码设置 + 确认密码
 * - 头像预览与上传
 * - 表单分步验证
 * - 注册成功后跳转登录页并预填用户名
 *
 * 路由：/m/register
 *
 * 依赖：
 * - useRegister: 注册逻辑
 * - MobileEntryShell: 布局壳组件
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, Upload, User, Postcard, Lock, Plus, View, Hide } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MobileEntryShell from './MobileEntryShell.vue'
import { useRegister } from '@/composables/useRegister'
import type { Result, Image } from '@/type'

const router = useRouter()
const { t } = useI18n()

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
const showPassword = ref(false)
const showConfirmPassword = ref(false)

/**
 * 进入注册流程的下一步（从步骤1到步骤2）
 *
 * 步骤：
 * 1. 验证步骤1的必填字段（昵称和用户名）
 * 2. 验证成功后切换到步骤2
 * 3. 验证失败时停留在当前步骤
 */
const nextRegisterStep = async () => {
  if (!registerFormRef.value) return

  // 验证步骤1的字段
  try {
    await registerFormRef.value.validateField(['nickname', 'username'])
    registerStep.value = 2
  } catch {
    // 验证失败
    return
  }
}

/**
 * 返回到注册流程的上一步（从步骤2回到步骤1）
 *
 * 说明：
 * - 用户点击返回按钮时调用
 * - 仅用于两步注册流程中的导航
 */
const prevRegisterStep = () => {
  registerStep.value = 1
}

/**
 * 处理用户注册提交
 *
 * 步骤：
 * 1. 设置加载状态
 * 2. 调用 useRegister 的 register() 方法执行注册
 * 3. 注册成功后：
 *    - 显示成功消息
 *    - 重定向到登录页并预填充用户名
 *    - 重置注册表单状态
 * 4. 无论成功失败，最终都清除加载状态
 */
const handleRegister = async () => {
  if (!registerFormRef.value) return
  isRegisterLoading.value = true
  try {
    const success = await register()
    if (success) {
      ElMessage.success(t('auth.register_success_msg'))
      // 重定向到首页并预填充用户名（统一入口，HomeView会根据设备类型渲染对应组件）
      router.replace({
        path: '/',
        query: { username: registerForm.value.username }
      })
      resetRegisterForm()
    }
  } catch (e) {
    // 错误由 useRegister 或全局错误处理器处理
    throw e
  } finally {
    isRegisterLoading.value = false
  }
}

const onAvatarSuccess = (response: unknown) => {
  handleAvatarSuccess(response as Result<Image | null>)
}
</script>

<template>
  <MobileEntryShell>
    <div class="view-header">
      <el-button link class="back-btn-icon" @click="registerStep === 1 ? router.back() : prevRegisterStep()">
        <el-icon :size="24">
          <ArrowLeft />
        </el-icon>
      </el-button>
      <h2>{{ t('auth.register') }}</h2>
      <div class="step-indicator">
        <div class="step-dot" :class="{ active: registerStep >= 1 }"></div>
        <div class="step-line" :class="{ active: registerStep >= 2 }"></div>
        <div class="step-dot" :class="{ active: registerStep >= 2 }"></div>
      </div>
    </div>

    <div class="form-container">
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerFormRules" class="auth-form" @submit.prevent>
        <!-- STEP 1 -->
        <div v-if="registerStep === 1" class="step-content">
          <!-- Avatar Section -->
          <div class="avatar-section">
            <el-upload class="avatar-uploader" action="/api/auth/register/upload" :show-file-list="false"
              :before-upload="beforeAvatarUpload" :on-success="onAvatarSuccess" accept="image/*">
              <div class="avatar-wrapper">
                <img v-if="registerForm.avatar.imageUrl" :src="registerForm.avatar.imageUrl" class="avatar-img" />
                <el-icon v-else class="avatar-placeholder-icon" :size="24">
                  <Upload />
                </el-icon>
                <div class="avatar-add-btn">
                  <el-icon class="w-3 h-3 text-white" :size="12">
                    <Plus />
                  </el-icon>
                </div>
              </div>
            </el-upload>
            <p class="avatar-hint">{{ t('auth.avatar_upload_hint') }}</p>
          </div>

          <!-- Form Fields -->
          <div class="form-fields">
            <el-form-item prop="nickname">
              <div class="input-wrapper">
                <el-icon class="input-icon" :size="20">
                  <Postcard />
                </el-icon>
                <input v-model="registerForm.nickname" type="text" class="custom-input"
                  :placeholder="t('auth.nickname_placeholder')" />
              </div>
            </el-form-item>

            <el-form-item prop="username">
              <div class="input-wrapper">
                <el-icon class="input-icon" :size="20">
                  <User />
                </el-icon>
                <input v-model="registerForm.username" type="text" class="custom-input"
                  :placeholder="t('auth.username_placeholder')" />
              </div>
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button type="primary" size="large" class="submit-btn premium-btn w-full" @click="nextRegisterStep">
              {{ t('common.next') }} <el-icon class="el-icon--right">
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>

        <!-- STEP 2 -->
        <div v-else class="step-content">
          <div class="user-summary">
            <img :src="registerForm.avatar.imageUrl || ''" class="summary-avatar" />
            <div class="flex flex-col">
              <span class="summary-nickname">{{ registerForm.nickname }}</span>
              <span class="summary-username">@{{ registerForm.username }}</span>
            </div>
          </div>

          <div class="password-group">
            <el-form-item prop="password">
              <div class="input-wrapper">
                <el-icon class="input-icon" :size="20">
                  <Lock />
                </el-icon>
                <input v-model="registerForm.password" :type="showPassword ? 'text' : 'password'" class="custom-input"
                  :placeholder="t('auth.password')" />
                <button type="button" class="password-toggle" aria-label="Toggle password visibility"
                  @click="showPassword = !showPassword">
                  <el-icon :size="20">
                    <View v-if="showPassword" />
                    <Hide v-else />
                  </el-icon>
                </button>
              </div>
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <div class="input-wrapper">
                <el-icon class="input-icon" :size="20">
                  <Lock />
                </el-icon>
                <input v-model="registerForm.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'"
                  class="custom-input" :placeholder="t('auth.confirm_password')" />
                <button type="button" class="password-toggle" aria-label="Toggle confirm password visibility"
                  @click="showConfirmPassword = !showConfirmPassword">
                  <el-icon :size="20">
                    <View v-if="showConfirmPassword" />
                    <Hide v-else />
                  </el-icon>
                </button>
              </div>
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button type="primary" size="large" class="submit-btn premium-btn w-full" :loading="isRegisterLoading"
              @click="handleRegister">
              {{ t('auth.register_btn') }}
            </el-button>
          </div>
        </div>
      </el-form>
    </div>
  </MobileEntryShell>
</template>

<style scoped>
/* 1. CSS Variables & Spacing System */
.step-content {
  /* Spacing Tokens */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 12px;
  --spacing-lg: 16px;
  --spacing-group: 16px;
  /* Group spacing */
  --spacing-field: 20px;
  /* Field spacing */
  --spacing-section: 24px;
  /* Section spacing */
  --spacing-xl: 24px;
  --spacing-2xl: 32px;
  --spacing-3xl: 48px;

  /* Layout */
  display: flex;
  flex-direction: column;
  gap: var(--spacing-section);
  width: 100%;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
}

.form-fields {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-field);
}

.password-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-group);
}

/* 2. Keyboard & Scroll Fix */
:deep(.auth-card) {
  overflow-y: auto;
  padding-bottom: calc(var(--spacing-2xl) + var(--safe-area-bottom, 0px));
}

/* 3. Header & Typography */
.view-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
}

.view-header h2 {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 12px;
  color: var(--text-main);
  line-height: 1.3;
}

.back-btn-icon {
  position: absolute;
  left: -8px;
  top: 0;
  color: var(--text-main);
}

/* Step Indicator */
.step-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--border-glass);
  transition: all 0.4s ease;
}

.step-line {
  width: 40px;
  height: 3px;
  background: var(--border-glass);
  border-radius: 2px;
  transition: all 0.4s ease;
}

.step-indicator .active {
  background: var(--primary);
  box-shadow: 0 0 10px color-mix(in srgb, var(--primary), transparent 60%);
}

/* 4. Form Styles */
.auth-form {
  display: flex;
  flex-direction: column;
}

/* Input Wrapper & Inputs */
.input-wrapper {
  width: 100%;
  height: 56px;
  background: var(--bg-input);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  padding: 0 16px;
  transition: all 0.3s var(--ease-out-expo);
  box-sizing: border-box;
}

.input-wrapper input {
  line-height: 1.5;
}

.input-wrapper:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary), transparent 85%);
}

/* Error State */
:deep(.el-form-item.is-error) .input-wrapper {
  border-color: var(--el-color-danger);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--el-color-danger), transparent 80%);
}

/* Fix element plus form item margin */
:deep(.el-form-item) {
  margin-bottom: 0;
}

.input-icon {
  margin-right: 12px;
  color: var(--text-sub);
}

.custom-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-main);
  height: 100%;
  width: 100%;
  font-size: 16px;
  padding: 0;
}

.custom-input::placeholder {
  color: var(--text-400);
}

.password-toggle {
  background: transparent;
  border: none;
  color: var(--text-400);
  padding: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-right: -8px;
}

/* Avatar Section */
.avatar-uploader {
  display: flex;
  justify-content: center;
}

.avatar-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-input);
  border: 1px dashed var(--el-border-color-light);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: border-color 0.3s;
}

.avatar-wrapper:hover {
  border-color: var(--primary);
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-placeholder-icon {
  color: var(--text-400);
}

.avatar-add-btn {
  position: absolute;
  bottom: -4px;
  right: 0;
  background: var(--primary);
  border-radius: 50%;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--bg-page);
}

.avatar-hint {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 0;
  /* Handled by gap */
  text-align: center;
  line-height: 1.45;
}

/* User Summary */
.user-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
}

.summary-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--bg-page);
}

.summary-nickname {
  font-weight: 700;
  color: var(--text-main);
  font-size: 16px;
  line-height: 1.25;
}

.summary-username {
  font-size: 14px;
  color: var(--text-sub);
  line-height: 1.4;
}

/* Buttons */
.premium-btn {
  height: 52px;
  border-radius: 16px;
  font-weight: 700;
  font-size: 16px;
  background: var(--primary-gradient);
  background: var(--primary);
  border: none;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.25);
  width: 100%;
  color: #fff;
}

.premium-btn:active {
  transform: scale(0.97);
}

/* 5. Responsive */
@media (max-width: 360px) {
  .step-content {
    --spacing-field: 16px;
    --spacing-section: 20px;
  }
}

@media (max-height: 700px) {
  :deep(.auth-card) {
    padding-bottom: calc(24px + var(--safe-area-bottom, 0px));
  }
}
</style>
