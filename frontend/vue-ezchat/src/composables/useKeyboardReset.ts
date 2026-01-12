/**
 * 键盘收起后视口重置 Composable
 *
 * 核心职责：
 * - 监听输入框失焦事件（键盘收起时机）
 * - 强制滚动到页面顶部，修复 iOS 视口偏移问题
 * - 触发重排以确保布局正确
 *
 * 使用示例：
 * ```vue
 * useKeyboardReset() // 在 setup 中调用即可
 * ```
 *
 * @module useKeyboardReset
 */
import { onMounted, onUnmounted } from 'vue'

/**
 * 键盘收起后视口重置 Hook
 *
 * 解决 iOS Safari 键盘收起后页面"卡住"的问题
 */
export function useKeyboardReset() {
  const resetScroll = () => {
    // Force scroll to top-left to fix "stuck" viewport
    window.scrollTo(0, 0)
    // Optional: Force a redraw if necessary
    document.body.style.display = 'none'
    // eslint-disable-next-line no-unused-expressions
    document.body.offsetHeight // trigger reflow
    document.body.style.display = ''
  }

  const handleFocusOut = (e: FocusEvent) => {
    const target = e.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
      setTimeout(resetScroll, 100) // Small delay to allow keyboard to anim out
    }
  }

  onMounted(() => {
    // Listen for focus out on the entire document (bubbling)
    document.addEventListener('focusout', handleFocusOut)
  })

  onUnmounted(() => {
    document.removeEventListener('focusout', handleFocusOut)
  })
}

export default useKeyboardReset
