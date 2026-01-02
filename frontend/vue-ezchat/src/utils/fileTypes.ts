/**
 * 文件类型/扩展名工具
 *
 * 业务目的：
 * - 前端上传前做“宽松但安全”的文件类型校验
 * - 以 MIME（file.type）为主，扩展名（file.name）为兜底（部分浏览器/来源可能没有 type）
 *
 * 注意：
 * - 这里的校验不应过严：最终以服务端校验为准
 * - 默认拒绝 SVG（image/svg+xml），避免潜在的脚本注入/渲染差异风险
 */

/**
 * 获取文件扩展名（小写，包含 "."）
 */
export function getFileExtensionLower(fileName?: string): string {
  if (!fileName) return ''
  const dot = fileName.lastIndexOf('.')
  if (dot === -1 || dot === fileName.length - 1) return ''
  return fileName.slice(dot).toLowerCase()
}

/**
 * 判断是否为允许的图片文件
 *
 * 规则：
 * - 优先使用 MIME：允许 image/*（默认排除 SVG）
 * - MIME 不可靠时，用扩展名兜底（常见图片格式）
 */
export function isAllowedImageFile(file: Pick<File, 'type' | 'name'>): boolean {
  const type = (file?.type || '').toLowerCase()
  if (type) {
    // 安全起见：默认拒绝 SVG
    if (type === 'image/svg+xml') return false
    return type.startsWith('image/')
  }

  const ext = getFileExtensionLower(file?.name)
  // 常见图片扩展名（尽量宽松，避免业务受限）
  const allowed = new Set([
    '.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.tif', '.tiff', '.avif', '.heic', '.heif',
  ])
  return allowed.has(ext)
}


