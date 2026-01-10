import { defineStore } from 'pinia'
import axios from 'axios'
import type { Image } from '@/type'
import { getImageUrlApi } from '@/api/Media'
import { isImage } from '@/utils/validators'

/**
 * ImageStore：统一管理图片 Blob 缓存与按需加载
 *
 * 业务目标：
 * - 缩略图（头像/列表图）在业务数据更新后异步预取 blobThumbUrl，加速渲染并减少重复请求
 * - 原图在“用户点开预览”时按需拉取 blobUrl（本地 URL 先试，失败再刷新预签名 URL）
 * - 业务数据替换/离开页面时清理无效 blob，避免内存泄漏
 */
export const useImageStore = defineStore('image', () => {
  // =========================
  // 1) 内部并发去重缓存（避免同一张图被重复拉取）
  // =========================
  const thumbPromiseMap = new Map<string, Promise<string | undefined>>()
  const originalPromiseMap = new Map<string, Promise<string | undefined>>()
  const avatarCache = new Map<string, Image>()
  const AVATAR_CACHE_LIMIT = 300

  // =========================
  // 2) 基础能力：拉取 blob 并转 objectURL
  // =========================
  const fetchBlobUrl = async (url: string): Promise<string | undefined> => {
    if (!url) return undefined
    try {
      const response = await axios.get(url, {
        responseType: 'blob',
        // 绕过 CDN 304 缓存导致 axios blob 请求失败的问题
        headers: {
          'Cache-Control': 'no-cache',
        },
      })
      return URL.createObjectURL(response.data)
    } catch {
      return undefined
    }
  }

  // =========================
  // 3) 缩略图：确保 blobThumbUrl 可用（头像/列表优先）
  // =========================
  const ensureThumbBlobUrl = async (img?: Image): Promise<string | undefined> => {
    if (!img) return undefined
    if (img.blobThumbUrl) return img.blobThumbUrl
    if (!img.imageName && !img.imageThumbUrl && !img.imageUrl) return undefined

    // GIF 特殊处理：直接使用原图（缩略图通常是静态第一帧，失去动画效果）
    const isGif = /\.gif($|\?)/i.test(img.imageName || img.imageUrl || '')
    if (isGif) {
      const originalBlob = await ensureOriginalBlobUrl(img)
      if (originalBlob) {
        // 同步设置 blobThumbUrl，使列表/头像场景可以直接使用
        img.blobThumbUrl = originalBlob

        // console.log('[ImageStore] GIF cached:', { blobUrl: img.blobUrl, blobThumbUrl: img.blobThumbUrl })
        return originalBlob
      }
    }

    const key = `${img.imageName || img.imageThumbUrl || img.imageUrl}:thumb`
    const existing = thumbPromiseMap.get(key)
    if (existing) return existing

    const task = (async () => {
      try {
        // 1) 本地 thumb URL 先试（最省）
        if (img.imageThumbUrl) {
          const blob = await fetchBlobUrl(img.imageThumbUrl)
          if (blob) {
            img.blobThumbUrl = blob
            return blob
          }
        }

        // 2) 回退本地原图 URL（头像场景允许用原图当“缩略图”展示）
        if (img.imageUrl) {
          const blob = await fetchBlobUrl(img.imageUrl)
          if (blob) {
            img.blobThumbUrl = blob
            return blob
          }
        }

        // 3) 本地 URL 都失败：刷新 imageUrl（预签名过期），再拉 blob
        if (img.imageName) {
          const res = await getImageUrlApi(img.imageName)
          const refreshed = res?.data
          if (refreshed) {
            img.imageUrl = refreshed
            const blob = await fetchBlobUrl(refreshed)
            if (blob) {
              img.blobThumbUrl = blob
              return blob
            }
          }
        }

        return undefined
      } finally {
        thumbPromiseMap.delete(key)
      }
    })()

    thumbPromiseMap.set(key, task)
    return task
  }

  // =========================
  // 4) 原图：按需获取 blobUrl（预览时）
  // =========================
  const ensureOriginalBlobUrl = async (img?: Image): Promise<string | undefined> => {
    if (!img) return undefined
    if (img.blobUrl) return img.blobUrl
    if (!img.imageName && !img.imageUrl) return img.imageUrl

    const key = `${img.imageName || img.imageUrl}:original`
    const existing = originalPromiseMap.get(key)
    if (existing) return existing

    const task = (async () => {
      try {
        // 1) 优先用本地 imageUrl（避免每次预览都打后端接口）
        if (img.imageUrl) {
          const localBlob = await fetchBlobUrl(img.imageUrl)
          if (localBlob) {
            img.blobUrl = localBlob
            return img.blobUrl
          }
        }

        // 2) 本地 URL 失效：刷新预签名 URL，并替换本地 imageUrl
        if (img.imageName) {
          const res = await getImageUrlApi(img.imageName)
          const refreshed = res?.data
          if (refreshed) {
            img.imageUrl = refreshed
            const blob = await fetchBlobUrl(refreshed)
            if (blob) img.blobUrl = blob
            return img.blobUrl || img.imageUrl
          }
        }

        return img.imageUrl
      } finally {
        originalPromiseMap.delete(key)
      }
    })()

    originalPromiseMap.set(key, task)
    return task
  }

  // =========================
  // 5) 并发执行工具：限制同时拉取数量
  // =========================
  const runWithConcurrency = async <T>(tasks: Array<() => Promise<T>>, limit = 6): Promise<T[]> => {
    const results: T[] = new Array(tasks.length)
    let idx = 0
    const workers = new Array(Math.min(limit, tasks.length)).fill(0).map(async () => {
      while (idx < tasks.length) {
        const cur = idx++
        const task = tasks[cur]
        if (!task) break
        results[cur] = await task()
      }
    })
    await Promise.all(workers)
    return results
  }

  /**
   * 图像数组去重：基于 imageName/assetId/imageUrl 去除重复项
   *
   * @param images 图像数组（可能包含 undefined）
   * @returns 去重后的图像数组
   */
  const deduplicateImages = (images: Array<Image | undefined>): Image[] => {
    return Array.from(
      new Map(
        images
          .filter((img): img is Image => Boolean(img))
          .map((img) => [img.imageName || img.assetId || img.imageUrl, img])
      ).values()
    ) as Image[]
  }

  /**
   * 批量预取缩略图 blob（用于头像/列表）
   * 内部自动去重，避免重复预取相同的图像
   */
  const prefetchThumbs = (images: Array<Image | undefined>, limit = 6) => {
    const uniqueImages = deduplicateImages(images)
    const tasks = uniqueImages.map((img) => () => ensureThumbBlobUrl(img))
    runWithConcurrency(tasks, limit).then(() => { })
  }

  // =========================
  // 6) 清理：释放 objectURL，避免内存泄漏
  // =========================
  const revokeImageBlobs = (img?: Image) => {
    if (!img) return
    if (img.blobUrl) URL.revokeObjectURL(img.blobUrl)
    if (img.blobThumbUrl) URL.revokeObjectURL(img.blobThumbUrl)
    img.blobUrl = ''
    img.blobThumbUrl = ''
  }

  /**
   * 清理一组图片的 blob
   */
  const revokeImagesBlobs = (images: Array<Image | undefined>) => {
    images.filter(Boolean).forEach((img) => revokeImageBlobs(img as Image))
  }

  /**
   * 业务数据替换时，清理“旧列表中不再存在”的图片 blob
   *
   * @param prev 旧图片列表
   * @param next 新图片列表
   */
  const revokeUnusedBlobs = (prev: Image[], next: Image[]) => {
    const nextKeys = new Set(next.map((i) => i.imageName).filter(Boolean))
    prev.forEach((img) => {
      if (!img.imageName) return
      if (!nextKeys.has(img.imageName)) revokeImageBlobs(img)
    })
  }

  const isSameAvatarUrl = (cached: Image, incoming: Image): boolean => {
    return cached.imageThumbUrl === incoming.imageThumbUrl && cached.imageUrl === incoming.imageUrl
  }

  const touchAvatarCache = (key: string) => {
    const cached = avatarCache.get(key)
    if (!cached) return
    avatarCache.delete(key)
    avatarCache.set(key, cached)
  }

  const evictAvatarCacheOverflow = () => {
    while (avatarCache.size > AVATAR_CACHE_LIMIT) {
      const oldestKey = avatarCache.keys().next().value
      if (!oldestKey) return
      const oldestImage = avatarCache.get(oldestKey)
      if (oldestImage) {
        revokeImageBlobs(oldestImage)
      }
      avatarCache.delete(oldestKey)
    }
  }

  const resolveAvatarFromCache = (key: string, incoming?: Image): Image | undefined => {
    if (!incoming) return undefined
    if (!key) return incoming

    const cached = avatarCache.get(key)
    if (!cached) {
      avatarCache.set(key, incoming)
      evictAvatarCacheOverflow()
      return incoming
    }

    if (isSameAvatarUrl(cached, incoming)) {
      if (incoming.imageName) {
        cached.imageName = incoming.imageName
      }
      cached.imageUrl = incoming.imageUrl
      cached.imageThumbUrl = incoming.imageThumbUrl
      if (typeof incoming.assetId === 'number') {
        cached.assetId = incoming.assetId
      }
      if (!cached.blobThumbUrl && incoming.blobThumbUrl) {
        cached.blobThumbUrl = incoming.blobThumbUrl
      }
      if (!cached.blobUrl && incoming.blobUrl) {
        cached.blobUrl = incoming.blobUrl
      }
      touchAvatarCache(key)
      return cached
    }

    revokeImageBlobs(cached)
    if (incoming.imageName) {
      cached.imageName = incoming.imageName
    }
    cached.imageUrl = incoming.imageUrl
    cached.imageThumbUrl = incoming.imageThumbUrl
    if (typeof incoming.assetId === 'number') {
      cached.assetId = incoming.assetId
    }
    touchAvatarCache(key)
    return cached
  }

  const pruneAvatarCache = (validKeys: Iterable<string>) => {
    const keep = new Set(validKeys)
    avatarCache.forEach((img, key) => {
      if (!keep.has(key)) {
        revokeImageBlobs(img)
        avatarCache.delete(key)
      }
    })
  }

  const resetState = () => {
    thumbPromiseMap.clear()
    originalPromiseMap.clear()
    avatarCache.forEach((img) => revokeImageBlobs(img))
    avatarCache.clear()
  }

  // =========================
  // 7) 默认头像生成与上传
  // =========================

  /**
   * 生成随机种子字符串（用于获取不同的头像图像）
   */
  const generateRandomSeed = (): string => {
    return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15)
  }

  /**
   * 生成默认头像 URL（从 DiceBear API，仅用于展示，不上传）
   * @param type 头像类型：'user' 为用户头像，'room' 为房间头像
   * @param seed 可选的自定义种子，如果不提供则生成随机种子
   * @returns DiceBear API 头像 URL
   */
  const generateDefaultAvatarUrl = (type: 'user' | 'room' = 'user', seed?: string): string => {
    const avatarSeed = seed || generateRandomSeed()
    if (type === 'room') {
      return `https://api.dicebear.com/9.x/identicon/svg?seed=${avatarSeed}`
    }
    // Default to 'user' type
    return `https://api.dicebear.com/9.x/bottts-neutral/svg?seed=${avatarSeed}`
  }

  /**
   * 如果用户未上传头像，则上传默认头像
   * @param currentAvatar 当前头像对象
   * @param type 头像类型：'user' 为用户头像，'room' 为房间头像
   * @returns Image 对象（如果已存在则返回原对象，否则上传默认头像后返回）
   */

  // TODO: 待后端实现滑动窗口token后，把默认头像上传接口全部替换为需要token验证的后端接口
  const uploadDefaultAvatarIfNeeded = async (currentAvatar?: Image, type: 'user' | 'room' = 'user'): Promise<Image> => {
    // 如果头像已存在，直接返回
    if (currentAvatar?.imageUrl || currentAvatar?.imageThumbUrl) {
      return currentAvatar
    }

    // 生成默认头像 URL 并获取
    const avatarUrl = generateDefaultAvatarUrl(type)
    const response = await fetch(avatarUrl)
    if (!response.ok) {
      throw new Error('Failed to fetch default avatar from DiceBear API')
    }
    const blob = await response.blob()

    // 上传到服务器
    const formData = new FormData()
    formData.append('file', blob, 'avatar.svg')

    const uploadResponse = await fetch('/api/auth/register/upload', {
      method: 'POST',
      body: formData
    })

    if (!uploadResponse.ok) {
      throw new Error('Failed to upload default avatar')
    }

    const result = await uploadResponse.json()
    if (result.code === 200 && result.data) {
      const imageData = result.data
      // 使用类型守卫验证返回的数据是否为 Image 类型
      if (isImage(imageData)) {
        return imageData
      }
      throw new Error('Invalid Image data structure in upload response')
    }

    throw new Error('Invalid upload response format')
  }

  return {
    ensureThumbBlobUrl,
    ensureOriginalBlobUrl,
    prefetchThumbs,
    revokeImageBlobs,
    revokeImagesBlobs,
    revokeUnusedBlobs,
    resolveAvatarFromCache,
    pruneAvatarCache,
    resetState,
    generateDefaultAvatarUrl,
    uploadDefaultAvatarIfNeeded,
  }
})
