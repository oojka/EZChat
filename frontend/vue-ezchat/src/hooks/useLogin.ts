import { reactive, ref, computed } from 'vue'
import { type LoginForm, type LoginUser, type Result } from '@/type'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { storeToRefs } from 'pinia'
import { Cooldown } from '@/utils/cooldown.ts'
import { showAppNotification, showWelcomeNotification } from '@/components/notification.ts'
import { useI18n } from 'vue-i18n'
import { useCooldown } from '@/hooks/useCooldown.ts'
import { type PasswordOptions, isValidUsername, isValidPassword } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { ElMessage } from 'element-plus'

// 登录冷却锁配置：3秒内最多5次尝试，超过则锁定15秒
const loginLock = new Cooldown(3000, 5, 15000)
// 密码验证选项：最小8位，最大20位，基础级别验证
const passwordOption: PasswordOptions = { min: 8, max: 20, level: 'basic' }

/**
 * 登录业务逻辑 Hook
 * 
 * 职责：
 * 1. 管理登录表单状态和验证规则
 * 2. 处理登录请求和冷却机制
 * 3. 登录成功后初始化应用状态并导航
 * 4. 提供表单重置功能
 * 
 * 设计原则：
 * - 专注于UI登录场景（无参数调用）
 * - 严格分离业务逻辑和UI展示
 * - 完整的错误处理和用户反馈
 * 
 * @returns {Object} 登录相关状态和方法
 */
export default function () {
  // ==================== 依赖注入 ====================
  const { t } = useI18n()                    // 国际化翻译函数
  const router = useRouter()                 // 路由实例，用于登录成功后导航
  const appStore = useAppStore()             // 应用状态管理
  const { initializeApp } = appStore         // 应用初始化方法

  const userStore = useUserStore()           // 用户状态管理

  // ==================== 冷却机制 ====================
  // 使用冷却机制防止频繁登录尝试
  const { isLocked, secondsLeft, tryExecute } = useCooldown(loginLock)

  // ==================== 表单状态 ====================
  const loginForm = reactive<LoginForm>({    // 登录表单数据
    username: '',
    password: '',
  })

  // ==================== 加载状态 ====================
  const isLoading = ref<boolean>(false)      // 登录按钮加载状态


  /**
   * 执行登录操作
   * 
   * 业务流程：
   * 1. 检查表单引用是否存在
   * 2. 执行表单验证（失败则终止）
   * 3. 通过冷却机制执行登录请求
   * 4. 登录成功后初始化应用并导航
   * 
   * 设计特点：
   * - 专为UI登录场景设计（无参数调用）
   * - 完整的错误处理和用户反馈
   * - 冷却机制防止频繁请求
   * - 登录成功后显示欢迎通知
   * 
   * @returns {Promise<void>}
   * @throws {AppError} 表单引用不存在或登录过程失败
   */
  const login = async () => {
    // ==================== 步骤1：表单验证 ====================
    // 检查表单引用是否存在（防御性编程）
    if (!loginForm.username && !loginForm.password) {
      // 请输入正确的用户名和密码
      ElMessage.error(t('validation.username_password_Invalid') || 'Please enter a valid username and password.')
      return
    }
    if (!isValidUsername(loginForm.username)) {
      // 请输入正确的用户名
      ElMessage.error(t('validation.username_Invalid') || 'Please enter a valid username.')
      return
    }
    if (!isValidPassword(loginForm.password)) {
      // 请输入正确的密码
      ElMessage.error(t('validation.password_Invalid') || 'Please enter a valid password.')
      return
    }
    // ==================== 步骤2：执行登录请求 ====================
    // 使用冷却机制包装登录逻辑，防止频繁尝试
    isLoading.value = true
    tryExecute(
      async () => {
        // 开始加载，显示加载状态
        try {
          // 调用用户Store的登录方法
          const data = await userStore.loginRequest(loginForm.username, loginForm.password)


          if (data) {
            // ==================== 步骤3：登录成功处理 ====================
            // 初始化应用状态（设置token、加载用户信息等）
            ElMessage.success(t('auth.login_success') || 'Login successful')
            await initializeApp(userStore.getAccessToken(), 'login')
            // ==================== 步骤4：状态更新和导航 ====================
            // 导航到聊天页面
            await router.push('/chat')
          }
        } catch (e) {
          // ==================== 错误处理 ====================
          if (isAppError(e)) {
            // 如果是已知的AppError，直接抛出
            throw e
          }
          // 未知错误，包装为AppError
          throw createAppError(
            ErrorType.UNKNOWN,
            'Login process failed',
            {
              severity: ErrorSeverity.ERROR,
              component: 'useLogin',
              action: 'login',
              originalError: e
            }
          )
        } finally {
          // ==================== 清理工作 ====================
          // 确保加载状态被正确清除
          setTimeout(() => {
            isLoading.value = false
            appStore.isAppLoading = false
            appStore.showLoadingSpinner = false
            appStore.loadingText = ''
          }, 300)
        }
      },
      // 冷却机制回调：当请求过于频繁时触发
      (sec) => {
        showAppNotification(t('auth.too_fast', { sec }), t('common.warning'), 'warning')
      }
    )
  }

  /**
   * 重置登录表单
   * 
   * 使用场景：
   * 1. 用户取消登录操作
   * 2. 登录成功后需要清空表单
   * 3. 组件卸载时清理状态
   * 
   * 操作内容：
   * - 清空用户名和密码字段
   * - 清除表单验证状态
   */
  const resetLoginForm = () => {
    loginForm.username = ''
    loginForm.password = ''
  }

  // ==================== 导出API ====================
  return {
    // 表单数据
    loginForm,        // 登录表单数据（双向绑定）

    // 状态
    isLoading,        // 登录按钮加载状态
    isLocked,         // 冷却锁定状态（true表示被锁定）
    secondsLeft,      // 冷却剩余秒数（当isLocked为true时有效）

    // 方法
    login,            // 执行登录操作
    resetLoginForm    // 重置登录表单
  }
}
