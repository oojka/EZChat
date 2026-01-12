<script setup lang="ts">
/**
 * 移动端登录页组件
 *
 * 功能：
 * - 用户名/密码登录表单
 * - 支持从注册页跳转时自动填充用户名
 * - 登录成功后跳转到主页
 * - 提供注册页面入口
 *
 * 路由：/m/login
 * 查询参数：username（可选，自动填充）
 *
 * 依赖：
 * - useLogin: 登录逻辑
 * - MobileEntryShell: 布局壳组件
 */
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { User, ArrowLeft, Lock } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import MobileEntryShell from './MobileEntryShell.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import useLogin from '@/composables/useLogin'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const { loginForm, isLoading, login } = useLogin()
const loginFormRef = ref<FormInstance>()

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await login()
}

const goToRegister = () => {
  router.push('/m/register')
}

onMounted(() => {
  const username = route.query.username
  if (username && typeof username === 'string') {
    loginForm.username = username
  }
})
</script>

<template>
  <MobileEntryShell>
    <div class="view-header">
      <el-button link class="back-btn-icon" @click="router.back()">
        <el-icon :size="24"><ArrowLeft /></el-icon>
      </el-button>
      <h2>{{ t('auth.login') }}</h2>
    </div>

    <div class="form-container">
      <el-form 
        ref="loginFormRef"
        :model="loginForm"
        class="auth-form"
        label-position="top"
        @submit.prevent
      >
        <div class="form-inputs">
          <el-form-item>
            <el-input 
              v-model="loginForm.username" 
              :placeholder="t('auth.username')" 
              size="large"
              :prefix-icon="User"
              class="premium-input"
            />
          </el-form-item>
          <el-form-item>
            <PasswordInput 
              v-model="loginForm.password" 
              :placeholder="t('auth.password')"
              class="premium-input"
              :prefix-icon="Lock"
            />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button 
            type="primary" 
            size="large" 
            class="submit-btn premium-btn" 
            :loading="isLoading"
            @click="handleLogin"
          >
            {{ t('auth.login') }}
          </el-button>
          
          <div class="register-link">
            <span>{{ t('auth.no_account') }}</span>
            <el-button link type="primary" @click="goToRegister">
              {{ t('auth.register_link') }}
            </el-button>
          </div>
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

/* Premium Input overrides */
:deep(.el-form-item__label) {
  display: none !important;
}

:deep(.premium-input .el-input__wrapper) {
  width: 100%;
  box-sizing: border-box;
  background: var(--input-bg);
  box-shadow: none !important;
  border: 1px solid transparent;
  border-radius: 16px;
  padding: 0; /* Remove default padding, handled by wrapper flex layout */
  transition: all 0.3s ease;
  height: 52px; /* Fixed height for consistency */
  display: flex;
  align-items: center;
}

/* Fix Icon Alignment - Unified Grid */
:deep(.premium-input .el-input__prefix) {
  margin-right: 0;
  display: flex;
  align-items: center;
  height: 100%;
  position: static; /* Override any absolute positioning */
}

:deep(.premium-input .el-input__prefix-inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px; /* Strict fixed width for icon alignment */
  min-width: 48px; /* Prevent shrinking */
  height: 100%;
  font-size: 20px;
  color: var(--text-sub);
}

:deep(.premium-input .el-input__inner) {
  color: var(--text-main);
  height: 100%;
  line-height: normal;
  padding: 0 16px 0 0 !important; /* Right padding for text, LEFT PADDING ZERO */
  text-indent: 0; /* Ensure no indentation */
  flex: 1; /* Fill remaining space */
  width: 100%; /* Required for flex child */
}

/* Suffix alignment for Password field */
:deep(.premium-input .el-input__suffix) {
  display: flex;
  align-items: center;
  height: 100%;
  right: 12px; /* Slight offset from right edge */
  position: relative; /* Ensure it stays in flow or absolute depending on need, but flex usually handles it */
}

:deep(.premium-input .el-input__suffix-inner) {
  display: flex;
  align-items: center;
  height: 100%;
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

.register-link {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--text-sub);
}
</style>