import type { Result, Image } from '@/type'
import request from '@/utils/request'

/**
 * 按 objectName 获取图片访问 URL（用于刷新预签名链接）
 *
 * - 后端接口：GET `/media/url?objectName=...`
 * - 业务目的：前端在点开大图预览时，按需获取最新 URL，避免预签名过期导致图片打不开
 */
export const getImageUrlApi = (objectName: string): Promise<Result<string>> =>
  request.get('/media/url?objectName=' + encodeURIComponent(objectName))

/**
 * 检查对象是否已存在（轻量级比对）
 * 
 * - 后端接口：GET `/media/check?rawHash=...`
 * - 业务目的：前端计算原始对象哈希后，先调用此接口比对，避免不必要的对象上传
 * 
 * @param rawHash 原始对象哈希（SHA-256 hex）
 * @returns 如果对象已存在，返回 Image 对象；不存在返回 null
 */
export const checkObjectExistsApi = (rawHash: string): Promise<Result<Image | null>> =>
  request.get('/media/check?rawHash=' + encodeURIComponent(rawHash))


