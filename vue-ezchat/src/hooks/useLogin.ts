import {reactive, ref} from 'vue'
import {type LoginInfo} from '@/type'
import {useRouter} from 'vue-router'
import {useAppStore} from '@/stores/appStore.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {storeToRefs} from 'pinia'
import {Cooldown} from '@/utils/cooldown.ts'
import {showAppNotification, showWelcomeNotification} from '@/utils/notification.ts'
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
    resetLoginForm
  }
}
