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
import { createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'

/**
 * 右侧卡片组件 - 登录/注册界面
 * 
 * 功能特点：
 * 1. 双面卡片设计：正面为登录界面，背面为注册界面
 * 2. 3D翻转动画效果
 * 3. 完整的登录和注册流程
 * 4. 多步骤注册向导
 * 5. 响应式设计和玻璃态视觉效果
 * 
 * 技术架构：
 * - 使用 Vue 3 Composition API + TypeScript
 * - 通过 Hooks 分离业务逻辑（useLogin, useRegister）
 * - Element Plus 组件库构建 UI
 * - Pinia 状态管理
 * - 完整的错误处理机制
 */

const { t } = useI18n()                    // 国际化翻译函数
const props = defineProps<{ active: boolean; flipped: boolean }>()  // 组件属性：激活状态和翻转状态
const emit = defineEmits<{ (e: 'flip'): void; (e: 'unflip'): void }>()  // 组件事件：翻转和取消翻转

// ==================== 业务逻辑 Hook 注入 ====================
// 登录相关状态和方法（业务逻辑完全封装在 useLogin hook 中）
const { loginForm, isLoading, isLocked, secondsLeft, login } = useLogin()

// 注册相关状态和方法（业务逻辑完全封装在 useRegister hook 中）
const { registerForm, registerFormRules, registerFormRef, register, beforeAvatarUpload, handleAvatarSuccess, resetRegisterForm } = useRegister()

// 图片存储管理
const imageStore = useImageStore()

// ==================== 注册流程状态 ====================
const registerStep = ref<1 | 2 | 3 | 4>(1)          // 注册步骤：1-头像，2-基本信息，3-密码，4-结果
const isRegistering = ref(false)                    // 注册提交中状态
const registrationResult = ref({ success: false, message: '' })  // 注册结果
const defaultAvatarUrl = ref('')                    // 默认头像 URL（用于展示，不上传）

/**
 * 注册进度百分比计算
 * 
 * 计算规则：
 * - 步骤1-3：根据当前步骤计算进度（33%, 66%, 99%）
 * - 步骤4且成功：显示100%
 * - 步骤4但失败：保持在步骤3的进度（99%）
 */
const progressPercentage = computed(() => {
  // 只有在步骤4且成功时才显示100%，否则根据当前步骤计算
  if (registerStep.value === 4 && registrationResult.value.success) {
    return 100
  }
  return (Math.min(registerStep.value, 3) / 3) * 100
})

// ==================== 生命周期钩子 ====================
// 页面加载时生成默认头像 URL（仅用于展示）
onMounted(() => {
  defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
})

// ==================== 卡片翻转控制 ====================
/**
 * 翻转卡片到注册面
 * 
 * 操作流程：
 * 1. 重置注册状态到第一步
 * 2. 清空注册结果
 * 3. 重置注册表单
 * 4. 确保默认头像 URL 已生成
 * 5. 触发翻转事件
 */
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

/**
 * 翻转卡片回登录面
 * 
 * 操作流程：
 * 1. 立即触发取消翻转事件
 * 2. 延迟重置注册状态（等待动画完成）
 * 3. 清空默认头像 URL（下次重新生成）
 */
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

// ==================== 头像上传处理 ====================
/**
 * 头像上传成功回调
 * 将上传结果传递给 useRegister hook 处理
 */
const onAvatarSuccess = (response: any) => { handleAvatarSuccess(response) }

// ==================== 注册步骤验证 ====================
/**
 * 验证当前注册步骤的表单字段
 * 
 * 验证规则：
 * - 步骤1（头像）：无需验证（可选）
 * - 步骤2（基本信息）：验证昵称和用户名
 * - 步骤3（密码）：验证密码和确认密码
 * 
 * @param step 当前步骤编号
 * @returns 验证是否通过
 */
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

/**
 * 进入下一步
 * 验证当前步骤通过后，进入下一个注册步骤
 */
const nextStep = async () => {
  if (await validateStep(registerStep.value) && registerStep.value < 3) registerStep.value++
}

/**
 * 返回上一步
 * 只能在步骤2-3之间返回
 */
const prevStep = () => {
  if (registerStep.value > 1 && registerStep.value <= 3) registerStep.value--
}

// ==================== 注册提交处理 ====================
/**
 * 处理注册提交
 * 
 * 业务流程：
 * 1. 验证步骤3（密码）的表单
 * 2. 检查并上传默认头像（如果用户未设置）
 * 3. 调用注册 API
 * 4. 根据结果更新UI状态
 * 
 * 设计特点：
 * - 失败时保持在步骤3，允许用户修改后重试
 * - 成功时跳转到步骤4显示结果
 * - 完整的错误处理和用户反馈
 */
const handleRegister = async () => {
  if (!await validateStep(3)) return
  isRegistering.value = true
  try {
    // 如果用户未设置头像（Image 对象为空或空串），上传默认头像
    if (!registerForm.value.avatar.objectUrl && !registerForm.value.avatar.objectThumbUrl) {
      registerForm.value.avatar = await imageStore.uploadDefaultAvatarIfNeeded(registerForm.value.avatar, 'user')
    }
    
    // 调用注册 API（业务逻辑在 useRegister hook 中）
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
    const errorMessage = t('common.error') || 'An error occurred' || e.message
    registrationResult.value = { success: false, message: errorMessage }
    // 不设置 registerStep = 4，保持在步骤3
    throw createAppError(
      ErrorType.NETWORK,
      errorMessage,
      {
        severity: ErrorSeverity.ERROR,
        component: 'useRegister',
        action: 'handleRegister'
      }
    )
  } finally {
    isRegistering.value = false
  }
}
</script>

<template>
  <!-- 
    双面卡片容器
    - 使用 CSS 3D 变换实现翻转效果
    - flipped 控制是否翻转（显示注册面）
    - active 控制阴影效果
  -->
  <div class="flip-card-inner" :class="{ 'is-flipped': flipped }">
    <!-- 正面：登录界面 -->
    <div class="flip-card-front" :class="{ 'has-shadow': active }">
      <!-- 登录头部：标题和副标题 -->
      <div class="login-header">
        <h2>{{ t('auth.login') }}</h2>
        <p class="login-subtitle">{{ t('auth.login_subtitle') }}</p>
      </div>

      <!-- 登录内容区域：表单输入 -->
      <div class="login-content">
        <!-- 
          登录表单
          - 使用 Element Plus 表单组件
          - 禁用自动验证消息（由 useLogin hook 处理）
          - 阻止表单默认提交行为
        -->
        <el-form :model="loginForm" :show-message="false"
          :validate-on-rule-change="false" class="login-form" @submit.prevent>
          <!-- 用户名输入框 -->
          <el-form-item class="username-input">
            <el-input v-model="loginForm.username" :placeholder="t('auth.username')" size="large">
              <template #prefix><el-icon>
                  <User />
                </el-icon></template>
            </el-input>
          </el-form-item>

          <!-- 密码输入框（使用自定义 PasswordInput 组件） -->
          <el-form-item class="password-input">
            <PasswordInput v-model="loginForm.password" :placeholder="t('auth.password')" @enter="login"/>
          </el-form-item>
        </el-form>
      </div>

      <!-- 登录底部：按钮和注册链接 -->
      <div class="login-footer">
        <!-- 
          登录按钮
          - 加载状态显示 loading 动画
          - 冷却锁定状态禁用按钮并显示倒计时
          - 点击触发 login 方法（业务逻辑在 useLogin hook 中）
        -->
        <el-button :loading="isLoading" :disabled="isLocked" type="primary" @click="login" class="login-btn"
          size="large">{{
            isLocked ? `${t('common.retry')} (${secondsLeft}s)` : t('auth.login') }}</el-button>
        <!-- 注册链接：点击翻转卡片到注册面 -->
        <div class="signup-link-wrapper"><span class="signup-text">{{ t('auth.no_account') }}</span><a
            class="signup-btn" @click.stop="onFlip">「{{ t('auth.register') }}」</a></div>
      </div>
    </div>

    <!-- 背面：注册界面 -->
    <div class="flip-card-back" :class="{ 'has-shadow': active }" @click.stop>
      <div class="register-container">
        <!-- 关闭按钮：点击返回登录面（步骤4除外） -->
        <el-button v-if="registerStep !== 4" class="close-flip-btn" :icon="Close" circle @click="onUnflip" />
        
        <!-- 注册头部：进度条和标题 -->
        <div class="register-header">
          <div class="progress-section">
            <!-- 进度条：显示当前注册进度 -->
            <el-progress :percentage="progressPercentage" :show-text="false" :stroke-width="4"
              :status="registerStep === 4 ? (registrationResult.success ? 'success' : 'exception') : ''"
              class="custom-progress" />
            <div class="step-label">{{ registerStep === 4 ? 'COMPLETED' : `Step ${registerStep} / 3` }}</div>
          </div>
          <h4>{{ registerStep === 4 ? t('auth.register_result') : t('auth.register') }}</h4>
        </div>
        <!-- 
          注册表单
          - 使用 Element Plus 表单验证规则
          - 标签位置在顶部
          - 隐藏必填星号
        -->
        <el-form :model="registerForm" label-position="top" :rules="registerFormRules" ref="registerFormRef"
          class="register-form-content" hide-required-asterisk>
          <!-- 步骤切换过渡动画 -->
          <transition name="el-fade-in-linear" mode="out-in">
            <!-- 步骤1：头像上传 -->
            <div v-if="registerStep === 1" key="step1" class="step-container avatar-step">
              <div class="avatar-upload-box shifted-down">
                <!-- 
                  头像上传组件
                  - 支持图片上传和预览
                  - 上传前验证（大小、格式）
                  - 上传成功回调
                -->
                <el-upload class="avatar-uploader-large" action="/api/auth/register/upload" :show-file-list="false"
                  :on-success="onAvatarSuccess" :before-upload="beforeAvatarUpload">
                  <!-- 已上传头像预览 -->
                  <div v-if="registerForm.avatar.objectThumbUrl" class="avatar-preview-lg"><img
                      :src="registerForm.avatar.objectThumbUrl" class="avatar-img" />
                    <div class="edit-mask-lg"><el-icon>
                        <Camera />
                      </el-icon><span>{{ t('common.change') }}</span></div>
                  </div>
                  <!-- 默认头像预览 -->
                  <div v-else-if="defaultAvatarUrl" class="avatar-preview-lg"><img
                      :src="defaultAvatarUrl" class="avatar-img" />
                    <div class="edit-mask-lg"><el-icon>
                        <Camera />
                      </el-icon><span>{{ t('common.change') }}</span></div>
                  </div>
                  <!-- 未选择头像占位符 -->
                  <div v-else class="placeholder-square-lg"><el-icon size="40">
                      <Picture />
                    </el-icon><span>{{ t('auth.select_image') }}</span></div>
                </el-upload>
                <div class="avatar-info-area">
                  <!-- 点击上传头像 -->
                  <p class="step-hint">{{ t('auth.avatar_upload_hint') || t('auth.avatar_hint') }}</p>
                </div>
                <!-- 隐藏的表单项：用于表单验证 -->
                <el-form-item prop="avatar" class="hidden-item" :show-message="false" />
              </div>
            </div>
            <!-- 步骤2：基本信息（昵称和用户名） -->
            <div v-else-if="registerStep === 2" key="step2" class="step-container">
              <div class="form-vertical-stack">
                <!-- 昵称输入框 -->
                <el-form-item :label="t('auth.nickname')" prop="nickname" class="nickname-input"><el-input
                    v-model="registerForm.nickname" :placeholder="t('auth.nickname_placeholder')" size="large"
                    :prefix-icon="Edit" @keydown.enter.prevent="nextStep" /></el-form-item>
                <!-- 用户名输入框 -->
                <el-form-item :label="t('auth.username')" prop="username" class="username-input" ><el-input v-model="registerForm.username"
                    :placeholder="t('auth.username_placeholder')" size="large" :prefix-icon="User"
                    @keydown.enter.prevent="nextStep" /></el-form-item>
              </div>
            </div>
            <!-- 步骤3：密码设置 -->
            <div v-else-if="registerStep === 3" key="step3" class="step-container">
              <div class="form-vertical-stack">
                <!-- 密码输入框 -->
                <el-form-item :label="t('auth.password')" prop="password" class="password-input">
                  <PasswordInput v-model="registerForm.password" :placeholder="t('auth.password_hint')"
                    @enter="handleRegister" />
                </el-form-item>
                <!-- 确认密码输入框 -->
                <el-form-item :label="t('auth.confirm_password')" prop="confirmPassword" class="confirm-password-input">
                  <PasswordInput v-model="registerForm.confirmPassword"
                    :placeholder="t('auth.confirm_password_placeholder')" @enter="handleRegister" />
                </el-form-item>
              </div>
            </div>
            <!-- 步骤4：注册结果 -->
            <div v-else key="step4" class="step-container result-step">
              <div class="result-content">
                <!-- 结果图标：成功显示勾选，失败显示叉号 -->
                <el-icon :class="['result-icon', registrationResult.success ? 'success' : 'error']">
                  <CircleCheckFilled v-if="registrationResult.success" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <!-- 结果标题 -->
                <h3 class="result-title">{{ registrationResult.success ? t('auth.register_success') :
                  t('auth.register_fail') }}</h3>
                <!-- 结果消息 -->
                <p class="result-message">{{ registrationResult.message }}</p>
              </div>
            </div>
          </transition>
          <!-- 注册操作按钮区域 -->
          <div class="register-actions">
            <!-- 步骤1：只有下一步按钮 -->
            <template v-if="registerStep === 1"><el-button type="primary" @click="nextStep" class="step-btn-full">{{
              t('common.next') }} <el-icon class="el-icon--right">
                  <ArrowRight />
                </el-icon></el-button></template>
            <!-- 步骤2：返回和下一步按钮 -->
            <template v-else-if="registerStep === 2"><el-button @click="prevStep" class="step-btn-half">{{
              t('common.back') }}</el-button><el-button type="primary" @click="nextStep" class="step-btn-half">{{
                  t('common.next') }}</el-button></template>
            <!-- 步骤3：返回和注册按钮 -->
            <template v-else-if="registerStep === 3"><el-button @click="prevStep" class="step-btn-half"
                :disabled="isRegistering">{{ t('common.back') }}</el-button><el-button type="primary"
                @click="handleRegister" class="step-btn-half" :loading="isRegistering">{{ t('auth.register_btn')
                }}</el-button></template>
            <!-- 步骤4：结果页面按钮 -->
            <template v-else>
              <!-- 失败时显示重试按钮 -->
              <el-button v-if="!registrationResult.success" @click="registerStep = 1"
                class="step-btn-half">{{ t('common.retry') }}</el-button>
              <!-- 成功时显示完整宽度登录按钮，失败时显示一半宽度 -->
              <el-button type="primary" @click="onUnflip"
                :class="registrationResult.success ? 'step-btn-full' : 'step-btn-half'">{{ t('auth.login_now')
                }}</el-button>
            </template>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 
  右侧卡片组件样式
  设计特点：
  - 玻璃态视觉效果（毛玻璃背景）
  - 3D翻转动画
  - 响应式布局
  - 暗色模式支持
*/

/* ==================== 卡片容器和翻转效果 ==================== */
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
