import imageCompression from 'browser-image-compression'
import { getFileExtensionLower } from '@/utils/fileTypes'

/**
 * 是否启用前端图片预处理（默认开启）
 * - VITE_ENABLE_CLIENT_IMAGE_PREPROCESS='false' 关闭
 */
const ENABLE_PREPROCESS = import.meta.env.VITE_ENABLE_CLIENT_IMAGE_PREPROCESS !== 'false'

/**
 * JPEG 质量（0~1），默认 0.9
 */
const JPEG_QUALITY = (() => {
  const raw = Number.parseFloat(import.meta.env.VITE_CLIENT_IMAGE_JPEG_QUALITY ?? '0.9')
  if (Number.isNaN(raw)) return 0.9
  return Math.min(1, Math.max(0.1, raw))
})()

/**
 * 是否允许把 PNG 转为 JPEG（默认不允许，避免截图/文字边缘发糊）
 */
const ALLOW_PNG_TO_JPEG = import.meta.env.VITE_CLIENT_PNG_TO_JPEG === 'true'

/**
 * PNG 转 JPEG 阈值（MB），默认 3MB
 */
const PNG_TO_JPEG_THRESHOLD_BYTES = (() => {
  const mb = Number.parseFloat(import.meta.env.VITE_CLIENT_PNG_TO_JPEG_THRESHOLD_MB ?? '3')
  const safeMb = Number.isNaN(mb) ? 3 : Math.min(50, Math.max(0.5, mb))
  return safeMb * 1024 * 1024
})()

/**
 * 图片压缩（前端预处理）
 *
 * 业务目的：
 * - 减少上传体积，提升上传速度与用户体验
 * - 在移动端/弱网下显著降低超时概率
 *
 * 约束：
 * - 优先标准化为 JPEG（兼容性更好，且有利于后端统一处理）
 * - 若压缩失败，必须回退到原始文件上传，避免阻断用户流程
 *
 * @param file 原始 File
 * @returns 压缩后的 File（或原文件）
 */
export async function compressImage(file: File): Promise<File> {
  // 支持开关：允许在生产环境/特定场景直接关闭预处理
  if (!ENABLE_PREPROCESS) return file

  // 只处理图片；非图片直接返回
  if (!file?.type?.startsWith('image/')) return file

  const ext = getFileExtensionLower(file.name)
  const type = (file.type || '').toLowerCase()

  // 说明：GIF 必须保留动效，前端不做重编码/压缩（交给后端/对象存储缩略图）
  if (type === 'image/gif' || ext === '.gif') return file

  // PNG 默认不处理（多用于截图/含文字），避免有损压缩导致发糊
  // 如果你明确允许 PNG 转 JPEG，则仅在超过阈值时才触发
  if ((type === 'image/png' || ext === '.png') && !(ALLOW_PNG_TO_JPEG && file.size >= PNG_TO_JPEG_THRESHOLD_BYTES)) {
    return file
  }

  // 重要约束（按你的要求）：不降低图片分辨率（不做任何 resize/downscale）
  // 这里仅做“格式统一（JPEG）+ 质量压缩 + 去元数据（由库内部重编码实现）”
  // 注意：browser-image-compression 不同版本导出的 Options 类型不稳定，这里用最小字段自定义类型兜底
  const options: {
    useWebWorker: boolean
    fileType: string
    initialQuality: number
    alwaysKeepResolution?: boolean
  } = {
    useWebWorker: true,
    fileType: 'image/jpeg',
    // 不降分辨率：不配置 maxWidthOrHeight/maxSizeMB，避免触发缩放策略
    // 仅通过质量压缩减少体积（像素尺寸保持不变）
    initialQuality: JPEG_QUALITY,
    // 若库版本支持该选项，可进一步保证不缩放（不在类型里也不影响运行）
    alwaysKeepResolution: true,
  }

  try {
    const compressed = await imageCompression(file, options as any)
    // 如果压缩后体积没有明显变小，则回退原文件（避免徒增 CPU 开销）
    if (compressed.size >= file.size * 0.98) return file

    // 确保文件名后缀为 .jpg，避免后端/对象存储按后缀判断类型时出现混乱
    const name = file.name?.replace(/\.[^/.]+$/, '') || 'image'
    return new File([compressed], `${name}.jpg`, { type: 'image/jpeg' })
  } catch (e) {
    console.warn('[WARN] [ImageCompressor] compress failed, fallback to original file', e)
    return file
  }
}


