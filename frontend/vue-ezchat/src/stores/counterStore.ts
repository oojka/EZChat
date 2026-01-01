import {computed, ref} from 'vue'
import {defineStore} from 'pinia'

/**
 * CounterStore：示例计数器（模板遗留）
 *
 * 业务说明：
 * - 该 Store 不参与聊天业务，仅用于演示 Pinia 写法
 */
export const useCounterStore = defineStore('counter', () => {
  const count = ref(0)
  /**
   * 计算属性示例：count 的 2 倍
   */
  const doubleCount = computed(() => count.value * 2)
  /**
   * Action 示例：自增计数
   */
  function increment() {
    count.value++
  }

  return { count, doubleCount, increment }
})
