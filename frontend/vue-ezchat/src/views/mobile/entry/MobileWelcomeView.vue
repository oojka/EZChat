<script setup lang="ts">
/**
 * 移动端欢迎页组件
 *
 * 功能：
 * - 三合一入口页面（访客加入、登录、注册）
 * - 快速登录表单（用户名/密码）
 * - 访客快速入口按钮
 * - 注册链接跳转
 *
 * 路由：/（移动端设备通过HomeView动态渲染）
 *       /m（兼容别名，重定向到/）
 *
 * 依赖：
 * - useLogin: 登录逻辑
 * - MobileEntryShell: 布局壳组件
 */
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { User, Search, ArrowRight } from '@element-plus/icons-vue'
import MobileEntryShell from './MobileEntryShell.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import useLogin from '@/composables/useLogin'
import { onMounted } from 'vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

// Login logic
const { loginForm, isLoading, login } = useLogin()

/**
 * 处理用户登录逻辑
 *
 * 功能：
 * - 调用 useLogin 的 login() 方法
 * - 登录成功后，useLogin 会自动处理用户状态和路由跳转
 * - 失败时由全局错误处理器处理
 */
const handleLogin = async () => {
  await login()
}

/**
 * 导航到访客加入页面
 *
 * 功能：
 * - 用户点击访客快速入口时调用
 * - 路由跳转到移动端访客加入页
 */
const goToGuest = () => {
  router.push('/m/guest')
}

/**
 * 导航到用户注册页面
 *
 * 功能：
 * - 用户点击注册链接时调用
 * - 路由跳转到移动端注册页
 */
const goToRegister = () => {
  router.push('/m/register')
}

/**
 * 组件挂载时的初始化逻辑
 *
 * 功能：
 * - 检查URL查询参数中是否有用户名
 * - 如果有用户名，预填充到登录表单
 * - 清除查询参数以避免URL泄露用户名
 */
onMounted(() => {
  const username = route.query.username
  if (username && typeof username === 'string') {
    loginForm.username = username
    // 清除查询参数以避免URL泄露用户名
    router.replace({ query: { ...route.query, username: undefined } })
  }
})
</script>

<template>
  <MobileEntryShell>
    <div class="welcome-container">
      <!-- 访客快速入口 -->
      <div class="guest-quick-access">
        <button class="guest-btn" @click="goToGuest">
          <el-icon :size="18"><Search /></el-icon>
          <span>{{ t('guest.title') }}</span>
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </div>

       <!-- 登录区域 -->
      <div class="login-section">
        <div class="section-header">
          <h2>{{ t('auth.login') }}</h2>
          <p>{{ t('auth.login_subtitle') }}</p>
        </div>

        <el-form 
          :model="loginForm"
          class="auth-form"
          @submit.prevent
        >
          <div class="form-inputs">
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
                @enter="handleLogin"
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
                {{ t('auth.register') }}
              </el-button>
            </div>
          </div>
        </el-form>
      </div>
    </div>
  </MobileEntryShell>
</template>

<style scoped>
.welcome-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Guest Quick Access */
.guest-quick-access {
  display: flex;
  justify-content: center;
}

.guest-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--input-bg);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.guest-btn:active {
  transform: scale(0.97);
  background: var(--input-focus-bg);
}

html.dark .guest-btn {
  border-color: rgba(255, 255, 255, 0.08);
}

/* Login Section */
.login-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section-header {
  text-align: center;
}

.section-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 8px;
}

.section-header p {
  font-size: 14px;
  color: var(--text-sub);
  margin: 0;
}

/* Form */
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

/* Premium Input overrides */
:deep(.el-form-item__label) {
  display: none !important;
}

:deep(.premium-input .el-input__wrapper) {
  background: var(--input-bg);
  box-shadow: none !important;
  border: 1px solid transparent;
  border-radius: 16px;
  padding: 12px 16px;
  transition: all 0.3s ease;
}

:deep(.premium-input .el-input__wrapper.is-focus) {
  background: var(--input-focus-bg);
  border-color: var(--primary);
  box-shadow: 0 4px 12px rgba(var(--primary-rgb), 0.1) !important;
}

:deep(.premium-input .el-input__inner) {
  color: var(--text-main);
  font-size: 16px;
}

/* Buttons */
.form-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 8px;
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
  transition: all 0.2s ease;
}

.premium-btn:active {
  transform: scale(0.97);
  box-shadow: 0 4px 10px rgba(99, 102, 241, 0.2);
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
