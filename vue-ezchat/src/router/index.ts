import {createRouter, createWebHistory} from 'vue-router'
import IndexView from '@/views/index/index.vue'
import LayoutView from '@/views/layout/index.vue'
import ChatView from '@/views/chat/index.vue'
import WelcomeView from '@/views/welcome/index.vue'
import ErrorView from '@/views/error/index.vue'
import {useAppStore} from '@/stores/appStore'

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

// 全局前置守卫
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

// 全局后置守卫
router.afterEach((to) => {
  const appStore = useAppStore()

  setTimeout(() => {
    appStore.isAppLoading = false
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
