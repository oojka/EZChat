import { defineStore } from 'pinia'
import axios from 'axios'
import type { Image } from '@/type'
import { getImageUrlApi } from '@/api/Media'

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

  // =========================
  // 2) 基础能力：拉取 blob 并转 objectURL
  // =========================
  const fetchBlobUrl = async (url: string): Promise<string | undefined> => {
    if (!url) return undefined
    try {
      const response = await axios.get(url, { responseType: 'blob' })
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
    if (!img.objectName && !img.objectThumbUrl && !img.objectUrl) return undefined

    const key = `${img.objectName || img.objectThumbUrl || img.objectUrl}:thumb`
    const existing = thumbPromiseMap.get(key)
    if (existing) return existing

    const task = (async () => {
      try {
        // 1) 本地 thumb URL 先试（最省）
        if (img.objectThumbUrl) {
          const blob = await fetchBlobUrl(img.objectThumbUrl)
          if (blob) {
            img.blobThumbUrl = blob
            return blob
          }
        }

        // 2) 回退本地原图 URL（头像场景允许用原图当“缩略图”展示）
        if (img.objectUrl) {
          const blob = await fetchBlobUrl(img.objectUrl)
          if (blob) {
            img.blobThumbUrl = blob
            return blob
          }
        }

        // 3) 本地 URL 都失败：刷新 objectUrl（预签名过期），再拉 blob
        if (img.objectName) {
          const res = await getImageUrlApi(img.objectName)
          const refreshed = res?.data
          if (refreshed) {
            img.objectUrl = refreshed
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
    if (!img.objectName && !img.objectUrl) return img.objectUrl

    const key = `${img.objectName || img.objectUrl}:original`
    const existing = originalPromiseMap.get(key)
    if (existing) return existing

    const task = (async () => {
      try {
        // 1) 优先用本地 objectUrl（避免每次预览都打后端接口）
        if (img.objectUrl) {
          const localBlob = await fetchBlobUrl(img.objectUrl)
          if (localBlob) {
            img.blobUrl = localBlob
            return img.blobUrl
          }
        }

        // 2) 本地 URL 失效：刷新预签名 URL，并替换本地 objectUrl
        if (img.objectName) {
          const res = await getImageUrlApi(img.objectName)
          const refreshed = res?.data
          if (refreshed) {
            img.objectUrl = refreshed
            const blob = await fetchBlobUrl(refreshed)
            if (blob) img.blobUrl = blob
            return img.blobUrl || img.objectUrl
          }
        }

        return img.objectUrl
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
   * 图像数组去重：基于 objectName/objectId/objectUrl 去除重复项
   * 
   * @param images 图像数组（可能包含 undefined）
   * @returns 去重后的图像数组
   */
  const deduplicateImages = (images: Array<Image | undefined>): Image[] => {
    return Array.from(
      new Map(
        images
          .filter(Boolean)
          .map((img) => [img.objectName || img.objectId || img.objectUrl, img])
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
    runWithConcurrency(tasks, limit).then(() => {})
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
    const nextKeys = new Set(next.map((i) => i.objectName).filter(Boolean))
    prev.forEach((img) => {
      if (!img.objectName) return
      if (!nextKeys.has(img.objectName)) revokeImageBlobs(img)
    })
  }

  const resetState = () => {
    thumbPromiseMap.clear()
    originalPromiseMap.clear()
  }

  return {
    ensureThumbBlobUrl,
    ensureOriginalBlobUrl,
    prefetchThumbs,
    revokeImageBlobs,
    revokeImagesBlobs,
    revokeUnusedBlobs,
    resetState,
  }
})


