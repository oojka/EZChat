import { onMounted, onUnmounted } from 'vue'

/**
 * 视口高度管理 Composable
 *
 * 业务目的：
 * - 解决 iOS Safari 动态视口问题（地址栏收起/展开、键盘弹出）
 * - 使用 visualViewport API 获取真实可视高度
 * - 将高度写入 CSS 变量 --app-height，供全局使用
 *
 * 使用方式：
 * - 在 App.vue 中调用一次 useViewportHeight()
 * - CSS 中使用 height: var(--app-height, 100dvh) 替代 100vh
 *
 * 兼容性：
 * - 优先使用 visualViewport（iOS Safari 13+, Chrome 61+）
 * - 回退到 window.innerHeight
 * - CSS 层面使用 100dvh 作为 fallback（iOS Safari 15.4+）
 */

/** CSS 变量名 */
const CSS_VAR_NAME = '--app-height'

/** 节流延迟（毫秒） */
const THROTTLE_DELAY = 100

/**
 * 更新 CSS 变量的核心函数
 */
function updateAppHeight() {
  // 优先使用 visualViewport，能准确反映键盘弹出后的可视区域
  const height = window.visualViewport?.height ?? window.innerHeight
  document.documentElement.style.setProperty(CSS_VAR_NAME, `${height}px`)
}

/**
 * 简单节流函数
 */
function throttle<T extends (...args: unknown[]) => void>(fn: T, delay: number): T {
  let lastCall = 0
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  return ((...args: Parameters<T>) => {
    const now = Date.now()
    const remaining = delay - (now - lastCall)

    if (remaining <= 0) {
      if (timeoutId) {
        clearTimeout(timeoutId)
        timeoutId = null
      }
      lastCall = now
      fn(...args)
    } else if (!timeoutId) {
      timeoutId = setTimeout(() => {
        lastCall = Date.now()
        timeoutId = null
        fn(...args)
      }, remaining)
    }
  }) as T
}

/**
 * 视口高度管理 Hook
 *
 * 在应用根组件中调用一次即可
 */
export function useViewportHeight() {
  const throttledUpdate = throttle(updateAppHeight, THROTTLE_DELAY)

  // orientationchange 处理器（需要命名引用以便清理）
  const handleOrientationChange = () => {
    // 方向变化后延迟更新，等待布局稳定
    setTimeout(updateAppHeight, 150)
  }

  onMounted(() => {
    // 初始化时立即设置一次
    updateAppHeight()

    // 监听 visualViewport 变化（键盘弹出/收起、地址栏变化）
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', throttledUpdate)
      window.visualViewport.addEventListener('scroll', throttledUpdate)
    }

    // 监听 window resize 作为补充（横竖屏切换等）
    window.addEventListener('resize', throttledUpdate)

    // 监听 orientationchange（部分设备需要）
    window.addEventListener('orientationchange', handleOrientationChange)
  })

  onUnmounted(() => {
    // 清理监听器
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', throttledUpdate)
      window.visualViewport.removeEventListener('scroll', throttledUpdate)
    }
    window.removeEventListener('resize', throttledUpdate)
    window.removeEventListener('orientationchange', handleOrientationChange)
  })
}

export default useViewportHeight
