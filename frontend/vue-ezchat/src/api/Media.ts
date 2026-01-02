import type { Result } from '@/type'
import request from '@/utils/request'

/**
 * 按 objectName 获取图片访问 URL（用于刷新预签名链接）
 *
 * - 后端接口：GET `/media/url?objectName=...`
 * - 业务目的：前端在点开大图预览时，按需获取最新 URL，避免预签名过期导致图片打不开
 */
export const getImageUrlApi = (objectName: string): Promise<Result<string>> =>
  request.get('/media/url?objectName=' + encodeURIComponent(objectName))


