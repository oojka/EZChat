import {defineStore} from 'pinia'
import {nextTick, ref, watch} from 'vue'
import {useUserStore} from '@/stores/userStore.ts'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useWebsocketStore} from '@/stores/websocketStore.ts'
import router from '@/router'
import i18n from '@/i18n'

export const useAppStore = defineStore('app', () => {
  const isAppLoading = ref(false)
  const showLoadingSpinner = ref(true)
  const loadingText = ref('初期化...')

  // =========================================
  // 1. 自动检测暗黑模式
  // =========================================
  const getInitialTheme = () => {
    const savedTheme = localStorage.getItem('theme')
    if (savedTheme) return savedTheme === 'dark'
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  const isDark = ref(getInitialTheme())

  watch(isDark, (val) => {
    if (val) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, { immediate: true })

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    if (!localStorage.getItem('theme')) {
      isDark.value = e.matches
    }
  })

  // =========================================
  // 2. 自动检测语言
  // =========================================
  const initLanguage = () => {
    const savedLocale = localStorage.getItem('locale')
    if (savedLocale) {
      i18n.global.locale.value = savedLocale
      return
    }

    const browserLang = navigator.language.toLowerCase()
    let targetLang = 'en' // 默认回退到英语

    if (browserLang.includes('zh-tw') || browserLang.includes('zh-hk')) targetLang = 'zh-tw'
    else if (browserLang.includes('zh')) targetLang = 'zh'
    else if (browserLang.includes('ja')) targetLang = 'ja'
    else if (browserLang.includes('ko')) targetLang = 'ko'
    else targetLang = 'en' // 其他情况统一回退到英语

    i18n.global.locale.value = targetLang
    localStorage.setItem('locale', targetLang)
  }

  initLanguage()

  const toggleTheme = async () => {
    if (!document.startViewTransition) {
      isDark.value = !isDark.value
      return
    }
    document.startViewTransition(async () => {
      isDark.value = !isDark.value
      await nextTick()
    })
  }

  const createRoomVisible = ref(false)

  const initializeApp = async (token?: string ,type: 'login' | 'refresh' = 'refresh') => {
    const userStore = useUserStore()
    const roomStore = useRoomStore()
    const websocketStore = useWebsocketStore()

    try {
      if (type === 'refresh') isAppLoading.value = true
      await userStore.initLoginUserInfo()
      const finalToken = token || userStore.loginUser.token

      if (!finalToken) {
        if (router.currentRoute.value.path !== '/') await router.replace('/')
        return
      }

      const tasks = []
      tasks.push(roomStore.initRoomList())
      if (websocketStore.status !== 'OPEN') websocketStore.initWS(finalToken)
      await Promise.all(tasks)

    } catch (error) {
      console.error('[ERROR] [AppStore] Initialization failed:', error)
    } finally {
      setTimeout(() => {
        isAppLoading.value = false
        showLoadingSpinner.value = true
        loadingText.value = 'Initializing...'
      }, 200)
    }
  }

  return {
    isAppLoading,
    showLoadingSpinner,
    loadingText,
    isDark,
    toggleTheme,
    createRoomVisible,
    initializeApp
  }
})
