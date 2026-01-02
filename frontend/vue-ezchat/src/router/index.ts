import {createRouter, createWebHistory} from 'vue-router'
import IndexView from '@/views/index/index.vue'
import LayoutView from '@/views/layout/index.vue'
import ChatView from '@/views/chat/index.vue'
import WelcomeView from '@/views/welcome/index.vue'
import ErrorView from '@/views/error/index.vue'
import {useAppStore} from '@/stores/appStore'

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
          meta: { title: 'Welcome　|　EZ Chat' },
        },
        {
          path: ':chatCode([0-9]{8})',
          name: 'ChatRoom',
          component: ChatView,
          meta: { title: 'Chat Room　|　EZ Chat' },
        },
      ],
    },
    {
      path: '/',
      component: IndexView,
      meta: { title: 'EZ Chat - Home' },
    },
    {
      path: '/error',
      component: ErrorView,
      meta: { title: '系统提示　|　EZ Chat' },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: (to) => ({
        path: '/error',
        query: { code: '404', title: '404 - Not Found　|　EZ Chat' },
      }),
    },
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
  const appStore = useAppStore()

  // 逻辑优化：
  // 判断是否在 /chat 体系内部切换（包括欢迎页和具体房间）
  const isChatSwitch =
    (from.name === 'ChatRoom' || from.name === 'Welcome')
    && to.name === 'ChatRoom'
  // 只有在非 /chat 内部切换，且路径确实发生变化时，才显示全局 Loading
  if (to.path !== from.path && !isChatSwitch) {
    appStore.isAppLoading = true
    appStore.loadingText = 'Loading...'
  }
  next()
})

/**
 * 全局后置守卫
 *
 * 业务目的：
 * - 关闭全局 Loading
 * - 统一设置 document.title（错误页根据错误码显示不同标题）
 */
router.afterEach((to) => {
  const appStore = useAppStore()

  setTimeout(() => {
    // 路由切换 Loading 不应该覆盖“应用初始化 Loading”
    if (!appStore.isAppInitializing) appStore.isAppLoading = false
  }, 200)

  const defaultTitle = 'EZ Chat'
  const errorCode = to.query.code as string
  const errorMap: Record<string, string> = {
    '404': 'NOT FOUND', '403': 'FORBIDDEN', '500': 'SERVER ERROR',
    '502': 'BAD GATEWAY', '503': 'SERVICE UNAVAILABLE',
  }

  let displayTitle = ''
  if (to.path === '/error' && errorCode) {
    const statusText = errorMap[errorCode] || 'ERROR'
    displayTitle = `${errorCode} - ${statusText}　|　EZ Chat`
  } else if (to.meta.title) {
    displayTitle = [...to.matched].reverse().find((record) => record.meta?.title)?.meta?.title as string
  }
  document.title = displayTitle ? `${displayTitle} | ${defaultTitle}` : defaultTitle
})

export default router
