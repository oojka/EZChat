import imageCompression from 'browser-image-compression'

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
  // 只处理图片；非图片直接返回
  if (!file?.type?.startsWith('image/')) return file

  // 说明：GIF 转 JPEG 会丢失动图效果，默认不处理 GIF
  if (file.type === 'image/gif') return file

  const options: imageCompression.Options = {
    maxSizeMB: 1.0,
    maxWidthOrHeight: 1920,
    useWebWorker: true,
    fileType: 'image/jpeg',
  }

  try {
    const compressed = await imageCompression(file, options)
    // 确保文件名后缀为 .jpg，避免后端/对象存储按后缀判断类型时出现混乱
    const name = file.name?.replace(/\.[^/.]+$/, '') || 'image'
    return new File([compressed], `${name}.jpg`, { type: 'image/jpeg' })
  } catch (e) {
    console.warn('[WARN] [ImageCompressor] compress failed, fallback to original file', e)
    return file
  }
}


