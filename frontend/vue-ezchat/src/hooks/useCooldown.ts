import {onUnmounted, ref} from 'vue'
import type {Cooldown} from '@/utils/cooldown'

/**
 * 将 Cooldown 逻辑转为响应式状态
 */
export function useCooldown(cooldownInstance: Cooldown) {
  const secondsLeft = ref(0)
  const isLocked = ref(false)
  let timer: number | null = null

  const startTimer = () => {
    if (timer) return

    isLocked.value = true
    secondsLeft.value = cooldownInstance.getRemainingTime()

    timer = window.setInterval(() => {
      const remaining = cooldownInstance.getRemainingTime()
      secondsLeft.value = remaining

      if (remaining <= 0) {
        stopTimer()
      }
    }, 1000)
  }

  const stopTimer = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    isLocked.value = false
    secondsLeft.value = 0
  }

  /**
   * 尝试执行并自动启动倒计时
   */
  const tryExecute = (onSuccess: () => void, onBlocked?: (sec: number) => void) => {
    if (cooldownInstance.canExecute()) {
      onSuccess()
    } else {
      startTimer()
      if (onBlocked) onBlocked(secondsLeft.value)
    }
  }

  onUnmounted(() => stopTimer())

  return {
    secondsLeft,
    isLocked,
    tryExecute
  }
}
