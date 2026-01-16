import { ref, watch, onUnmounted } from 'vue'
import type { Ref } from 'vue'
import type { Image } from '@/type'

/**
 * 图片加载配置选项
 */
export type ImageLoaderOptions = {
  /** GIF 优先使用原图（默认 true） */
  preferOriginalForGif?: boolean
  /** 非 GIF 优先使用缩略图（默认 true） */
  preferThumb?: boolean
}

/**
 * 图片加载 composable
 *
 * 核心功能：
 * - 主URL/备选URL退避加载逻辑
 * - GIF特殊处理（优先原图保留动效）
 * - 竞态条件防护（loadSeq）
 * - 预加载并切换机制
 *
 * @param imageRef 响应式图片对象引用
 * @param options 加载配置选项
 * @returns 响应式状态：currentUrl, isLoading, isError
 */
export function useImageLoader(
  imageRef: Ref<Image | undefined>,
  options: ImageLoaderOptions = {}
) {
  const { preferOriginalForGif = true, preferThumb = true } = options

  /** 当前显示的图片URL */
  const currentUrl = ref<string>('')
  /** 是否加载失败 */
  const isError = ref(false)
  /** 是否正在加载 */
  const isLoading = ref(false)
  /** 加载序列号，用于防止竞态条件 */
  let loadSeq = 0
  /** 当前加载的 Image 实例（用于中止） */
  let currentImg: HTMLImageElement | null = null

  /**
   * 判断图片是否为GIF格式
   */
  const isGifImage = (image?: Image): boolean => {
    if (!image) return false
    const name = image.imageName || ''
    const url = image.imageUrl || ''
    const thumbUrl = image.imageThumbUrl || ''
    const blobUrl = image.blobUrl || ''
    const blobThumbUrl = image.blobThumbUrl || ''
    const hint = `${name} ${url} ${thumbUrl} ${blobUrl} ${blobThumbUrl}`.toLowerCase()
    return hint.includes('.gif')
  }

  /**
   * 获取图片URL候选列表（主URL和备选URL）
   */
  const getUrlCandidates = (image?: Image): { primary: string; fallback: string } => {
    if (!image) {
      return { primary: '', fallback: '' }
    }

    const thumb = image.blobThumbUrl || image.imageThumbUrl || ''
    const original = image.blobUrl || image.imageUrl || ''
    const isGif = isGifImage(image)

    // GIF文件特殊处理：优先使用原图（保留动效）
    if (isGif && preferOriginalForGif) {
      return {
        primary: original || thumb,
        fallback: original && thumb && original !== thumb ? thumb : ''
      }
    }

    // 非GIF文件：根据配置决定优先级
    if (preferThumb) {
      return {
        primary: thumb || original,
        fallback: thumb && original && thumb !== original ? original : ''
      }
    }

    return {
      primary: original || thumb,
      fallback: original && thumb && original !== thumb ? thumb : ''
    }
  }

  /**
   * 预加载图片并切换到最佳可用URL
   */
  const preloadAndSwap = (urls: string[]) => {
    // 中止之前的加载
    if (currentImg) {
      currentImg.onload = null
      currentImg.onerror = null
      currentImg = null
    }

    if (!urls.length) {
      currentUrl.value = ''
      isError.value = true
      isLoading.value = false
      return
    }

    const primary = urls[0]
    if (primary === currentUrl.value && currentUrl.value) {
      isError.value = false
      isLoading.value = false
      return
    }

    const seq = ++loadSeq
    isLoading.value = true
    isError.value = false

    const tryLoad = (index: number) => {
      const target = urls[index]
      if (!target) {
        if (seq === loadSeq) {
          currentUrl.value = ''
          isError.value = true
          isLoading.value = false
        }
        return
      }

      const img = new window.Image()
      currentImg = img

      img.onload = () => {
        if (seq !== loadSeq) return
        currentUrl.value = target
        isError.value = false
        isLoading.value = false
        currentImg = null
      }

      img.onerror = () => {
        if (seq !== loadSeq) return
        if (index + 1 < urls.length) {
          tryLoad(index + 1)
          return
        }
        currentUrl.value = ''
        isError.value = true
        isLoading.value = false
        currentImg = null
      }

      img.src = target
    }

    tryLoad(0)
  }

  /**
   * 手动触发错误状态（供 <img @error> 使用）
   */
  const handleError = () => {
    currentUrl.value = ''
    isError.value = true
    isLoading.value = false
  }

  // 监听图片属性变化，重新加载图片
  const stopWatch = watch(
    imageRef,
    (image) => {
      const { primary, fallback } = getUrlCandidates(image)
      if (primary && primary === currentUrl.value && !isError.value) {
        return
      }
      const urls: string[] = []
      if (primary) urls.push(primary)
      if (fallback && fallback !== primary) urls.push(fallback)
      preloadAndSwap(urls)
    },
    { immediate: true }
  )

  // 清理
  onUnmounted(() => {
    stopWatch()
    if (currentImg) {
      currentImg.onload = null
      currentImg.onerror = null
      currentImg = null
    }
  })

  return {
    /** 当前显示的图片URL */
    currentUrl,
    /** 是否正在加载 */
    isLoading,
    /** 是否加载失败 */
    isError,
    /** 手动触发错误状态 */
    handleError
  }
}
