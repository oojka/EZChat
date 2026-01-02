/**
 * 图片上传大小限制（MB）
 * 
 * 业务说明：
 * - 统一所有图片上传功能的大小限制
 * - 包括：头像上传、聊天图片上传、创建聊天室头像上传
 */
export const MAX_IMAGE_SIZE_MB = 10

/**
 * 图片上传大小限制（字节）
 * 
 * 用于直接比较文件大小，避免重复计算
 */
export const MAX_IMAGE_SIZE_BYTES = MAX_IMAGE_SIZE_MB * 1024 * 1024

