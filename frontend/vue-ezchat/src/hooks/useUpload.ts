import {ref} from 'vue'
import {ElMessage, type UploadRequestOptions} from 'element-plus'
import axios from 'axios'
import {useUserStore} from '@/stores/userStore'
import { MAX_IMAGE_SIZE_MB } from '@/constants/imageUpload'

// 定义上传结果类型
type UploadResult = {
  url: string
  filename?: string
}

export function useUpload(uploadApiUrl: string) {
  const isUploading = ref(false)
  const uploadProgress = ref(0)

  /**
   * 自定义上传方法，适配 el-upload 的 http-request
   */
  const customUpload = async (options: UploadRequestOptions) => {
    const { file, onSuccess, onError, onProgress } = options

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

    const formData = new FormData()
    formData.append('file', file)

    try {
      // 获取 Token
      const userStore = useUserStore()
      const token = userStore.getToken

      // 2. 发送请求
      // 注意：这里直接使用 axios 而不是封装好的 api 实例，是为了更方便地控制 onUploadProgress
      // 如果您的 api 封装支持 onUploadProgress，也可以替换
      const response = await axios.post(uploadApiUrl, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            uploadProgress.value = percent
            // Element Plus 的 onProgress 期望的是 UploadProgressEvent，这里将 axios 事件转成最小兼容结构
            // 使用 as any 绕过类型检查（可接受的第三方库类型兼容性异常）
            onProgress?.({ percent } as any) // 通知 el-upload 更新进度条
          }
        }
      })

      // 3. 处理响应
      // 假设后端返回格式为 { code: 200, data: { url: '...' } }
      if (response.data && response.data.code === 200) {
        const result: UploadResult = response.data.data
        onSuccess(result) // 通知 el-upload 成功
        return result
      } else {
        throw new Error(response.data.message || 'Upload failed')
      }

    } catch (error: any) {
      console.error('Upload Error:', error)
      ElMessage.error('画像のアップロードに失敗しました')
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
