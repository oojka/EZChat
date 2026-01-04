import {createRouter, createWebHistory} from 'vue-router'
import IndexView from '@/views/index/index.vue'
import LayoutView from '@/views/layout/index.vue'
import ChatView from '@/views/chat/index.vue'
import JoinView from '@/views/Join/index.vue'
import WelcomeView from '@/views/welcome/index.vue'
import ErrorView from '@/views/error/500.vue'
import NotFoundView from '@/views/error/404.vue'
import { useRoute } from 'vue-router'
import {useAppStore} from '@/stores/appStore'
import {useWebsocketStore} from '@/stores/websocketStore'
import i18n from '@/i18n'

/**
 * 路由配置
 *
 * 设计要点：
 * - `/`：登录/注册入口
 * - `/chat`：聊天主布局（子路由包含欢迎页与具体房间）
 * - `/error`：统一错误展示页（通过 query/state 传递错误码）
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/chat',
      component: LayoutView,
      children: [
        {
          path: '',
          name: 'Welcome',
          component: WelcomeView,
          meta: { title: 'Welcome' },
        },
        {
          path: ':chatCode([0-9]{8})',
          name: 'ChatRoom',
          component: ChatView,
          meta: { title: 'Chat Room' },
        },
        {
          path: '/guest/:chatCode([0-9]{8})',
          name: 'GuestChatRoom',
          component: ChatView,
          meta: { title: 'Chat Room - Guest Access' },
        },
      ],
    },
    {
      path: '/',
      name: 'Home',
      component: IndexView,
      meta: { title: 'EZ Chat - Home' },
    },
    {
      path: '/invite/:inviteCode([0-9A-Za-z]{16,24})',
      name: 'Invite',
      component: JoinView,
      meta: { title: 'Verifying Invitation... ' },
    },
    {
      path: '/Join/:chatCode([0-9]{8})',
      name: 'Join',
      component: JoinView,
      meta: { title: 'Join ChatRoom' },
    },
    {
      path: '/error',
      name: 'Error',
      component: ErrorView,
      meta: { title: '' },
    },
    {
      path: '/:pathMatch(.*)*', 
      name: 'NotFound',
      component: NotFoundView,
      meta: { title: '404 Not Found' },
    }
  ],
})

/**
 * 全局前置守卫
 *
 * 业务目的：
 * - 控制全局 Loading（减少在聊天页内部切换时的“闪白/闪屏”）
 *
 * 注意：
 * - 当前工程**没有**在 Router 层做“登录权限拦截”（登录态主要由 `request.ts` 401 处理 + App 初始化逻辑驱动）
 * - 如未来需要强制权限，请在此处校验 token，并对 `/chat/**` 做重定向
 */
router.beforeEach((to, from, next) => {
  const route = useRoute()
  const appStore = useAppStore()
  const websocketStore = useWebsocketStore()

  // 业务规则：离开 /chat 体系（去到首页/错误页等）时，自动断开 WebSocket
  // 目的：避免用户不在聊天页时仍保持 WS 长连接占用资源，也防止后台重连带来额外流量。
  const isLeavingChat = from.path.startsWith('/chat') && !to.path.startsWith('/chat')
  if (isLeavingChat) {
    websocketStore.resetState()
  }

  // 如果目标是 /error，显示全白全屏遮蔽（无转圈无文字）
  if (to.name === 'Error' || to.name === 'NotFound') {
    appStore.loadingBgWhite = true
    appStore.isAppLoading = true
    appStore.showLoadingSpinner = false
  } else {
    appStore.loadingBgWhite = false
    appStore.showLoadingSpinner = true
  }

  // 逻辑优化：
  // 判断是否在 /chat 体系内部切换（包括欢迎页和具体房间）
  const isChatSwitch =
    (from.name === 'ChatRoom' || from.name === 'Welcome')
    && to.name === 'ChatRoom'
  // 只有在非 /chat 内部切换，且路径确实发生变化时，才显示全局 Loading（错误页已在上面单独处理）
  if (to.path !== '/error' && to.path !== from.path && !isChatSwitch) {
    appStore.isAppLoading = true
    // 进入聊天体系（登录后跳转 / 刷新回到 /chat）：使用"初始化..."提示
    // 其他普通路由切换：使用"加载中..."提示
    const isEnterChat = to.path.startsWith('/chat') && !from.path.startsWith('/chat')
    // 注意：i18n.global.t() 返回类型为 string | VNode，但对于这些键，返回值为 string
    // 使用类型守卫进行运行时类型检查，避免类型断言
    const initText = i18n.global.t('common.initializing')
    const loadingText = i18n.global.t('common.loading')
    appStore.loadingText = isEnterChat
      ? (typeof initText === 'string' ? initText : '')
      : (typeof loadingText === 'string' ? loadingText : '')
  }
  next()
})

/**
 * 全局后置守卫
 *
 * 业务目的：
 * - 关闭全局 Loading
 * - 统一设置 document.title（错误页标题置为空字符串，由组件自己处理）
 */
router.afterEach((to) => {
  const appStore = useAppStore()

  setTimeout(() => {
    // 路由切换 Loading 不应该覆盖"应用初始化 Loading"
    if (!appStore.isAppInitializing) appStore.isAppLoading = false
  }, 100)

  const defaultTitle = 'EZ Chat'
  
  // 设置标题（错误页标题置为空字符串，由 error/index.vue 组件自己处理）
  if (to.name === 'Error') {
    document.title = ''
  } else {
    // 其他页面正常处理标题
    const displayTitle = to.meta.title 
      ? ([...to.matched].reverse().find((record) => record.meta?.title)?.meta?.title as string)
      : ''
    document.title = displayTitle ? `${displayTitle} | ${defaultTitle}` : defaultTitle
  }
  
  // Favicon 处理：错误页由 error/index.vue 组件自己处理（移除 favicon）
  // 其他页面的 favicon 由 App.vue 统一管理（使用 /favicon_io/favicon.ico）
})

export default router
