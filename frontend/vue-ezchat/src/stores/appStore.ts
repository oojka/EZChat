import { defineStore } from 'pinia'
import { nextTick, ref, watch } from 'vue'
import { useUserStore } from '@/stores/userStore.ts'
import { useAppRefreshToken } from '@/composables/useAppRefreshToken'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useWebsocketStore } from '@/stores/websocketStore.ts'
import { useMessageStore } from '@/stores/messageStore.ts'
import { showWelcomeNotification } from '@/components/notification.ts'
import router from '@/router'
import i18n from '@/i18n'

// 支持的语言列表（as const 用于推导联合类型）
const SUPPORTED_LOCALES = ['ja', 'en', 'zh', 'ko', 'zh-tw'] as const
type SupportedLocale = typeof SUPPORTED_LOCALES[number]

/**
 * 类型守卫：判断字符串是否为支持的 locale
 *
 * @param val 任意字符串
 */
const isSupportedLocale = (val: string): val is SupportedLocale => {
  return (SUPPORTED_LOCALES as readonly string[]).includes(val)
}

/**
 * AppStore：管理“应用级”状态
 *
 * 包括：
 * - 全局 Loading（刷新/路由切换时的遮罩与文案）
 * - 主题（暗黑模式）与语言（i18n locale）初始化与切换
 * - 应用初始化流程（恢复登录态 → 拉取房间列表 → 建立 WS）
 */
export const useAppStore = defineStore('app', () => {
  // 全局 Loading 状态：用于控制页面骨架/遮罩
  const isAppLoading = ref(false)
  // 是否显示 Loading 遮罩
  const showLoadingSpinner = ref(true)
  // 注意：默认值会在 initLanguage() 后再用 i18n 文案覆盖，避免多语言下出现“写死某一语言”的问题
  const loadingText = ref('Initializing...')
  // App 初始化中的保护标记：用于避免路由守卫过早关闭 Loading
  const isAppInitializing = ref(false)
  // refresh 全屏 Loading 的“最短展示时长”
  const MIN_REFRESH_LOADING_MS = 300
  // Loading 背景是否为全白（用于错误页等场景）
  const loadingBgWhite = ref(false)

  // =========================================
  // 1. 自动检测暗黑模式
  // =========================================
  /**
   * 获取初始主题
   *
   * 优先级：
   * 1) localStorage 的用户显式选择
   * 2) 系统 prefers-color-scheme
   */
  const getInitialTheme = () => {
    const savedTheme = localStorage.getItem('theme')
    if (savedTheme) return savedTheme === 'dark'
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  const isDark = ref(getInitialTheme())

  watch(isDark, (val) => {
    // 将主题状态写入 html class，Element Plus 暗黑变量会自动生效
    if (val) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }, { immediate: true })

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    // 用户未手动选择主题时，才跟随系统变化（避免“系统变化覆盖用户选择”）
    if (!localStorage.getItem('theme')) {
      isDark.value = e.matches
    }
  })

  // =========================================
  // 2. 自动检测语言
  // =========================================
  /**
   * 初始化语言
   *
   * 业务目的：首次访问时根据浏览器语言自动选择 locale，并落盘，保证后续刷新一致。
   */
  const initLanguage = () => {
    const savedLocale = localStorage.getItem('locale')
    // 1) 优先使用用户保存的语言；若不在白名单内则忽略，避免写入非法 locale 导致 i18n 异常
    if (savedLocale && isSupportedLocale(savedLocale)) {
      i18n.global.locale.value = savedLocale
      return
    }

    const browserLang = navigator.language.toLowerCase()
    // 2) 否则按浏览器语言推断，并保证类型为 SupportedLocale
    let targetLang: SupportedLocale = 'en' // 默认回退到英语

    if (browserLang.includes('zh-tw') || browserLang.includes('zh-hk')) targetLang = 'zh-tw'
    else if (browserLang.includes('zh')) targetLang = 'zh'
    else if (browserLang.includes('ja')) targetLang = 'ja'
    else if (browserLang.includes('ko')) targetLang = 'ko'
    else targetLang = 'en' // 其他情况统一回退到英语

    i18n.global.locale.value = targetLang
    localStorage.setItem('locale', targetLang)
  }

  initLanguage()
  // 初始化语言后，补齐 Loading 文案（与当前 locale 对齐）
  // 注意：i18n.global.t() 返回类型为 string | VNode，但对于 'common.initializing' 键，返回值为 string
  // 使用类型断言是因为我们确定该键返回 string 类型（非模板字符串）
  const initText = i18n.global.t('common.initializing')
  loadingText.value = typeof initText === 'string' ? initText : ''

  /**
   * 切换主题（暗黑/明亮）
   *
   * 业务目的：支持更丝滑的切换动效；若浏览器不支持 View Transitions，则降级为直接切换。
   */
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

  /**
   * 切换语言（非错误页使用）：使用 View Transitions API 做同款淡入淡出。
   * 错误页如需保持“强制白天模式”布局稳定，可继续使用直接赋值 locale 的方式。
   */
  /**
   * 切换语言
   *
   * @param lang 目标语言（如 zh/en/ja/ko/zh-tw）
   */
  const changeLanguage = async (lang: string) => {
    // 只允许切换到支持的语言，避免写入非法 locale
    if (!lang || !isSupportedLocale(lang) || i18n.global.locale.value === lang) return

    const apply = async () => {
      // 1) 切换 locale
      i18n.global.locale.value = lang
      // 2) 落盘，确保刷新后仍保持该语言
      localStorage.setItem('locale', lang)
      await nextTick()
    }

    if (!document.startViewTransition) {
      await apply()
      return
    }

    document.startViewTransition(apply)
  }

  const createRoomVisible = ref(false)
  const joinDialogVisible = ref(false)
  const userSettingsDialogVisible = ref(false)

  /**
   * 设置 favicon
   * <p>
   * 业务目的：
   * - 统一管理 favicon 设置逻辑
   * - 支持在所有组件中复用
   *
   * @param faviconPath 可选，favicon 路径，默认为 '/favicon_io/favicon.ico'
   */
  const setFavicon = (faviconPath: string = '/favicon_io/favicon.ico') => {
    const faviconLinks = document.querySelectorAll("link[rel*='icon']")

    if (faviconLinks.length === 0) {
      // 如果不存在 favicon link，创建一个
      const link = document.createElement('link')
      link.rel = 'icon'
      link.href = faviconPath
      document.head.appendChild(link)
    } else {
      // 如果已存在，更新所有 favicon 链接的 href
      faviconLinks.forEach(link => {
        const linkEl = link as HTMLLinkElement
        linkEl.href = faviconPath
      })
    }
  }

  /**
   * 移除 favicon
   * <p>
   * 业务目的：
   * - 统一管理 favicon 移除逻辑
   * - 支持在所有组件中复用
   */
  const removeFavicon = () => {
    const faviconLinks = document.querySelectorAll("link[rel*='icon']")
    faviconLinks.forEach(link => link.remove())
  }

  /**
   * 通用的加载状态管理函数
   * 
   * 业务目的：统一管理异步操作的加载状态，避免在各个组件/composable中重复实现
   * 
   * @param text 加载时显示的文本
   * @param fn 要执行的异步函数
   * @returns 异步函数的执行结果
   */
  const runWithLoading = async <T>(text: string, fn: () => Promise<T>): Promise<T> => {
    isAppLoading.value = true
    showLoadingSpinner.value = true
    loadingText.value = text

    try {
      return await fn()
    } finally {
      isAppLoading.value = false
      showLoadingSpinner.value = false
      loadingText.value = ''
    }
  }

  /**
   * 应用初始化（页面刷新/登录后进入聊天页）
   *
   * 业务流程：
   * 1) 恢复登录态（localStorage → userStore）
   * 2) 如果没有 token，则回到首页
   * 3) 并行拉取房间列表，并建立 WebSocket 连接
   *
   * @param token 可选：登录成功后直接传入 token，减少一次读取
   * @param type 初始化触发来源（login/refresh）用于控制 Loading 表现
   */
  const initializeApp = async (
    token?: string,
    type: 'login' | 'refresh' | 'guest' = 'refresh',
    options?: { waitForRoute?: string; maxWaitMs?: number }
  ) => {
    const userStore = useUserStore()
    const roomStore = useRoomStore()
    const websocketStore = useWebsocketStore()
    const messageStore = useMessageStore()
    const { refreshOnceIfNeeded } = useAppRefreshToken()
    const refreshLoadingStartAt = type === 'refresh' ? Date.now() : 0
    let finalTokenForWs: string | undefined

    try {
      // 登录 / refresh / 访客：全屏遮蔽应显示"初始化..."而不是 "Loading..."
      if (type === 'refresh' || type === 'login' || type === 'guest') {
        loadingText.value = i18n.global.t('common.initializing') || 'Initializing...'
        isAppInitializing.value = true
        isAppLoading.value = true
        showLoadingSpinner.value = true
      }

      // 0) App 初始化前先清空所有 Store 内存态，避免历史会话残留
      websocketStore.resetState()
      roomStore.resetState()
      messageStore.resetState()
      if (type != 'guest') {
        const keepAuth = !!token && userStore.hasToken()
        userStore.resetState({ keepAuth })
      }
      // 1) refresh 场景“关键链路”：优先尝试刷新 accessToken，再恢复 token
      // 用户详情/房间列表等慢请求放到后台，避免黑屏转圈时间被拉长
      // 如果访客，则不恢复登录态
      let refreshedToken: string | null = null
      if (type === 'refresh') {
        const refreshResult = await refreshOnceIfNeeded()
        refreshedToken = refreshResult.token
        if (refreshResult.hadToken && !refreshedToken) {
          await userStore.logout({ showDialog: true })
          return
        }
      }
      userStore.restoreLoginStateIfNeeded('guest')
      const finalToken = token || refreshedToken || userStore.getAccessToken()
      finalTokenForWs = finalToken

      if (!finalToken) {
        // 未登录：统一走 logout 弹窗逻辑
        await userStore.logout({ showDialog: true })
        return
      }
      void userStore.syncLoginUserInfo()
      await roomStore.initRoomList()

    } catch (error) {
      console.error('[ERROR] [AppStore] Initialization failed:', error)
    } finally {
      const elapsed = Date.now() - refreshLoadingStartAt
      const remain = Math.max(0, MIN_REFRESH_LOADING_MS - elapsed)
      setTimeout(() => {
        const waitForRoute = options?.waitForRoute
        const maxWaitMs = options?.maxWaitMs ?? 1500

        const finalizeInitialization = () => {
          if (type === 'refresh' || type === 'login' || type === 'guest') {
            isAppLoading.value = false
            showLoadingSpinner.value = false
            loadingText.value = ''
            // 2) 遮蔽撤掉后再解除"初始化中"，允许 chatView/消息开始加载
            isAppInitializing.value = false
          }

          // 3) 初始化完成后再连接 WS（不等待握手完成）
          if (finalTokenForWs) {
            websocketStore.initWS(finalTokenForWs)
          }
          // 4) login 场景显示欢迎通知（使用 watch 监听用户信息加载）
          if (type === 'login') {
            // 如果已经加载完成，直接显示
            if (userStore.loginUserInfo?.avatar?.blobThumbUrl || userStore.loginUserInfo?.avatar?.blobUrl) {
              showWelcomeNotification(userStore.loginUserInfo)
            } else {
              // 否则监听用户信息变化，加载完成后自动显示
              watch(
                () => userStore.loginUserInfo,
                (newInfo) => {
                  if (newInfo) {
                    showWelcomeNotification(newInfo)
                  }
                },
                {
                  once: true,
                  deep: true,
                } // 只触发一次，深度监听
              )
            }
          }
        }

        if (!waitForRoute || router.currentRoute.value.path.startsWith(waitForRoute)) {
          finalizeInitialization()
          return
        }

        let finished = false
        const stop = watch(
          () => router.currentRoute.value.path,
          (path) => {
            if (finished || !path.startsWith(waitForRoute)) return
            finished = true
            stop()
            finalizeInitialization()
          },
        )

        setTimeout(() => {
          if (finished) return
          finished = true
          stop()
          finalizeInitialization()
        }, maxWaitMs)
      }, remain);
    }
  }


  return {
    isAppLoading,
    showLoadingSpinner,
    loadingText,
    isAppInitializing,
    loadingBgWhite,
    isDark,
    toggleTheme,
    changeLanguage,
    createRoomVisible,
    joinDialogVisible,
    userSettingsDialogVisible,
    initializeApp,
    setFavicon,
    removeFavicon,
    runWithLoading,
  }
})
