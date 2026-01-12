/**
 * 键盘可见性检测 Composable
 *
 * 核心职责：
 * - 检测移动端虚拟键盘的弹出/收起状态
 * - 使用 visualViewport API 计算高度差值
 * - 处理屏幕方向变化时的基准线更新
 *
 * 使用示例：
 * ```vue
 * const { isKeyboardVisible } = useKeyboardVisible()
 * // isKeyboardVisible.value 为 true 表示键盘已弹出
 * ```
 *
 * @module useKeyboardVisible
 */
import { ref, onMounted, onUnmounted } from 'vue'

/** 键盘弹出的高度阈值（像素） */
const KEYBOARD_THRESHOLD = 150

/**
 * 键盘可见性检测 Hook
 *
 * @returns isKeyboardVisible - 响应式 ref，true 表示键盘可见
 */
export function useKeyboardVisible() {
  const isKeyboardVisible = ref(false)
  let initialHeight = 0

  const updateBaseline = () => {
    initialHeight = window.visualViewport?.height ?? window.innerHeight
  }

  const checkKeyboard = () => {
    if (!window.visualViewport) return

    const currentHeight = window.visualViewport.height
    const diff = initialHeight - currentHeight
    isKeyboardVisible.value = diff > KEYBOARD_THRESHOLD
  }

  const handleOrientationChange = () => {
    setTimeout(() => {
      updateBaseline()
      isKeyboardVisible.value = false
    }, 150)
  }

  onMounted(() => {
    updateBaseline()

    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', checkKeyboard)
    }
    window.addEventListener('orientationchange', handleOrientationChange)
  })

  onUnmounted(() => {
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', checkKeyboard)
    }
    window.removeEventListener('orientationchange', handleOrientationChange)
  })

  return { isKeyboardVisible }
}

export default useKeyboardVisible
