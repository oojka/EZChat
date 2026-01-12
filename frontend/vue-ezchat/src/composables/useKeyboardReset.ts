import { onMounted, onUnmounted } from 'vue'

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
