import { ref, onMounted, onUnmounted } from 'vue'

const KEYBOARD_THRESHOLD = 150

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
