import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 移动端断点判断 Composable
 *
 * 业务目的：
 * - 提供统一的移动端/桌面端判断逻辑
 * - 基于 768px 断点（xs < 768px 为移动端）
 * - 响应式监听窗口变化
 *
 * 使用方式：
 * ```ts
 * const { isMobile } = useIsMobile()
 * // isMobile.value 为 true 表示当前为移动端
 * ```
 */

/** 移动端断点阈值（像素） */
const MOBILE_BREAKPOINT = 768

/** 媒体查询字符串 */
const MOBILE_QUERY = `(max-width: ${MOBILE_BREAKPOINT - 1}px)`

/**
 * 移动端断点判断 Hook
 *
 * @returns isMobile - 响应式 ref，true 表示移动端
 */
export function useIsMobile() {
  const isMobile = ref(false)
  let mediaQuery: MediaQueryList | null = null

  /**
   * 媒体查询变化处理器
   */
  const handleChange = (e: MediaQueryListEvent | MediaQueryList) => {
    isMobile.value = e.matches
  }

  onMounted(() => {
    // 创建媒体查询并立即检测当前状态
    mediaQuery = window.matchMedia(MOBILE_QUERY)
    isMobile.value = mediaQuery.matches

    // 监听变化
    mediaQuery.addEventListener('change', handleChange)
  })

  onUnmounted(() => {
    // 清理监听器
    if (mediaQuery) {
      mediaQuery.removeEventListener('change', handleChange)
    }
  })

  return {
    /** 是否为移动端（响应式） */
    isMobile,
    /** 移动端断点阈值 */
    MOBILE_BREAKPOINT,
  }
}

/**
 * 非响应式版本：直接获取当前是否为移动端
 * 用于不需要响应式监听的场景（如初始化判断）
 */
export function checkIsMobile(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia(MOBILE_QUERY).matches
}

export default useIsMobile
