import {reactive, ref} from 'vue'
import {type LoginInfo, type LoginUser, type Result} from '@/type'
import {useRouter} from 'vue-router'
import {useAppStore} from '@/stores/appStore.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {storeToRefs} from 'pinia'
import {Cooldown} from '@/utils/cooldown.ts'
import {showAppNotification, showWelcomeNotification} from '@/components/notification.ts'
import {useI18n} from 'vue-i18n'
import {useCooldown} from '@/hooks/useCooldown.ts'
import {USERNAME_REG} from '@/utils/validators.ts'

const loginLock = new Cooldown(5000, 3, 15000)

export default function () {
  const { t } = useI18n()
  const router = useRouter()
  const appStore = useAppStore()
  const { showLoadingSpinner } = storeToRefs(appStore)
  const { initializeApp } = appStore

  const userStore = useUserStore()
  const { loginRequest } = userStore

  const { isLocked, secondsLeft, tryExecute } = useCooldown(loginLock)

  const loginFormRef = ref()
  const loginForm = reactive<LoginInfo>({
    username: '',
    password: '',
  })

  // 登录校验规则：移除密码格式校验，仅保留必填校验
  const loginFormRules = {
    username: [
      { required: true, message: '', trigger: 'manual' },
      { pattern: USERNAME_REG, message: '', trigger: 'manual' }
    ],
    password: [
      { required: true, message: '', trigger: 'manual' }
    ]
  }

  const isLoading = ref<boolean>(false)

  /**
   * 执行登录（可复用的登录逻辑）
   * <p>
   * 业务目的：
   * - 提供可复用的登录逻辑，供其他 hook 调用
   * - 执行登录 API 调用和 token 保存
   * - 不处理导航，由调用方决定后续流程
   *
   * @param username 用户名
   * @param password 密码
   * @returns Promise<Result<LoginUser>>
   */
  const executeLogin = async (username: string, password: string): Promise<Result<LoginUser>> => {
    // 验证用户名和密码
    if (!username || username.trim() === '') {
      throw new Error(t('validation.username_required') || '用户名不能为空')
    }
    if (!USERNAME_REG.test(username.trim())) {
      throw new Error('用户名格式错误')
    }
    if (!password || password.trim() === '') {
      throw new Error(t('validation.password_required') || '密码不能为空')
    }

    try {
      const data = await loginRequest(username.trim(), password)
      if (data) {
        return {
          status: 1,
          code: 200,
          message: '',
          data: data,
        }
      }
      return {
        status: 0,
        code: 500,
        message: '登录失败',
        data: null!,
      }
    } catch (error) {
      return {
        status: 0,
        code: 500,
        message: error instanceof Error ? error.message : '登录失败',
        data: null!,
      }
    }
  }

  const login = async () => {
    if (!loginFormRef.value) return

    try {
      await loginFormRef.value.validate()
    } catch (error) {
      return
    }

    tryExecute(
      async () => {
        isLoading.value = true
        try {
          const data = await loginRequest(loginForm.username, loginForm.password)
          if (data) {
            await initializeApp(data.token, 'login')
            if (userStore.loginUserInfo) showWelcomeNotification(userStore.loginUserInfo)
            await new Promise(resolve => setTimeout(resolve, 300))
            isLoading.value = false
            showLoadingSpinner.value = true
            await router.push('/chat')
          }
        } catch (e) {
          isLoading.value = false
          // ElMessage.error(t('auth.login_failed'))
        }
      },
      (sec) => {
        showAppNotification(t('auth.too_fast', { sec }), t('common.warning'), 'warning')
      }
    )
  }

  const resetLoginForm = () => {
    loginForm.username = ''
    loginForm.password = ''
    if (loginFormRef.value) loginFormRef.value.clearValidate()
  }

  return {
    loginForm,
    loginFormRef,
    loginFormRules,
    isLoading,
    isLocked,
    secondsLeft,
    login,
    executeLogin,
    resetLoginForm
  }
}
