import { ref } from 'vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'
import type { Image, Result } from '@/type'
import { uploadMessageImageApi } from '@/api/Message'
import { uploadAvatarApi } from '@/api/Auth'
import i18n from '@/i18n'

/**
 * 上传类型枚举
 */
export type UploadType = 'message' | 'avatar'

/**
 * 上传 Hook
 *
 * 业务逻辑：
 * - 封装文件上传逻辑，统一使用 request 实例管理（可被限流拦截器拦截）
 * - 支持消息图片上传和头像上传两种类型
 * - 自动处理文件校验、上传进度、错误处理
 *
 * @param type 上传类型：'message' 消息图片 | 'avatar' 头像
 * @returns 上传相关的状态和方法
 */
export function useUpload(type: UploadType) {
  const isUploading = ref(false)
  const uploadProgress = ref(0)

  /**
   * 自定义上传方法，适配 el-upload 的 http-request
   *
   * 业务逻辑：
   * 1. 校验文件类型和大小
   * 2. 根据上传类型调用对应的 API
   * 3. 处理上传进度回调
   * 4. 统一错误处理
   */
  const customUpload = async (options: UploadRequestOptions) => {
    const { file, onSuccess, onError, onProgress } = options
    const { t } = i18n.global

    // 1. 校验文件类型和大小
    const isImage = file.type.startsWith('image/')
    const isLtMaxSize = file.size / 1024 / 1024 < MAX_IMAGE_SIZE_MB

    if (!isImage) {
      ElMessage.error('画像ファイルのみアップロード可能です')
      return
    }
    if (!isLtMaxSize) {
      ElMessage.error('画像サイズは10MB以下にしてください')
      return
    }

    isUploading.value = true
    uploadProgress.value = 0

    try {
      // 2. 根据类型调用对应的 API
      let result: Result<Image>

      if (type === 'message') {
        result = await uploadMessageImageApi(file, (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            uploadProgress.value = percent
            // Element Plus 的 onProgress 期望的是 UploadProgressEvent
            onProgress?.({ percent } as any)
          }
        })
      } else {
        result = await uploadAvatarApi(file, (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            uploadProgress.value = percent
            onProgress?.({ percent } as any)
          }
        })
      }

      // 3. 处理响应（后端返回 Result<Image> 格式）
      if (result.status === 1 && result.data) {
        // 转换为 el-upload 期望的格式
        const uploadResult = {
          url: result.data.imageUrl,
          filename: result.data.imageName
        }
        onSuccess(uploadResult)
        return uploadResult
      } else {
        throw new Error(result.message || 'Upload failed')
      }

    } catch (error: any) {
      console.error('Upload Error:', error)
      ElMessage.error(t('api.image_upload_failed'))
      onError(error)
    } finally {
      isUploading.value = false
    }
  }

  return {
    isUploading,
    uploadProgress,
    customUpload
  }
}
