/**
 * 无限滚动 Hook
 * <p>
 * 提供通用的分页加载、滚动监听和自动加载更多功能。
 * 适用于消息列表、聊天列表等需要分页加载的场景。
 */
import { nextTick, onMounted, onUnmounted, ref, type Ref } from 'vue'

export type UseInfiniteScrollOptions = {
  /**
   * 是否正在加载中
   */
  loading: Ref<boolean>
  /**
   * 是否没有更多数据
   */
  noMore: Ref<boolean>
  /**
   * 加载更多数据的回调函数
   */
  onLoadMore: () => Promise<void> | void
  /**
   * 列表元素的引用（可选，如果不提供则使用返回的 listRef）
   */
  listRef?: Ref<HTMLElement | null>
  /**
   * 滚动容器的选择器（可选，默认使用 listRef）
   */
  rootSelector?: string
  /**
   * IntersectionObserver 的阈值（可选，默认 0.1）
   */
  threshold?: number
}

export type UseInfiniteScrollReturn = {
  /**
   * 列表容器的引用
   */
  listRef: Ref<HTMLElement | null>
  /**
   * 加载触发器的引用（用于 IntersectionObserver）
   */
  loadTriggerRef: Ref<HTMLElement | null>
  /**
   * 是否在底部
   */
  isAtBottom: Ref<boolean>
  /**
   * 滚动到底部
   */
  scrollToBottom: () => Promise<void>
  /**
   * 重置滚动状态（用于切换列表时）
   */
  resetScrollState: () => void
}

/**
 * 无限滚动组合式函数
 * <p>
 * 提供分页加载、滚动监听、自动加载更多等功能。
 *
 * @param options 配置选项
 * @returns 返回列表引用、触发器引用和相关方法
 */
export function useInfiniteScroll(options: UseInfiniteScrollOptions): UseInfiniteScrollReturn {
  const {
    loading,
    noMore,
    onLoadMore,
    listRef: externalListRef,
    threshold = 0.1,
  } = options

  // 使用外部提供的 listRef 或创建新的
  const listRef = externalListRef || ref<HTMLElement | null>(null)
  const loadTriggerRef = ref<HTMLElement | null>(null)
  const isAtBottom = ref(true)

  let observer: IntersectionObserver | null = null

  /**
   * 滚动到底部
   */
  const scrollToBottom = async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }

  /**
   * 处理滚动事件
   */
  const handleScroll = () => {
    if (!listRef.value) return
    const scrollPos = Math.abs(listRef.value.scrollTop)
    isAtBottom.value = scrollPos < 10
  }

  /**
   * 加载历史数据
   */
  const handleLoadHistory = async () => {
    if (loading.value || noMore.value) return
    await onLoadMore()
  }

  /**
   * 设置 IntersectionObserver
   */
  const setupObserver = () => {
    if (observer) observer.disconnect()
    
    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && !loading.value && !noMore.value) {
          handleLoadHistory()
        }
      },
      { root: listRef.value, threshold }
    )
    
    if (loadTriggerRef.value) {
      observer.observe(loadTriggerRef.value)
    }
  }

  /**
   * 重置滚动状态
   */
  const resetScrollState = () => {
    isAtBottom.value = true
    if (listRef.value) {
      listRef.value.scrollTop = 0
    }
    setupObserver()
  }

  // 生命周期
  onMounted(() => {
    setupObserver()
    listRef.value?.addEventListener('scroll', handleScroll)
  })

  onUnmounted(() => {
    if (observer) observer.disconnect()
    listRef.value?.removeEventListener('scroll', handleScroll)
  })

  return {
    listRef,
    loadTriggerRef,
    isAtBottom,
    scrollToBottom,
    resetScrollState,
  }
}
