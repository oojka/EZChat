import type { ChatRoom, Message, Result, Image } from '@/type'
import request from '@/utils/request'

export type GetMessageListApiReq = {
  chatCode: string
  cursorSeqId?: number // 游标序列号 (为空=查最新)
}

/**
 * 拉取消息列表（支持序列号分页）
 *
 * - 后端接口：GET `/message?chatCode=...&cursorSeqId=...`
 * - 业务目的：首次进入房间/上拉加载历史时获取消息
 */
export const getMessageListApi = (
  data: GetMessageListApiReq
): Promise<Result<{
  chatRoom: ChatRoom
  messageList: Message[]
}>> =>
  request.get('/message?chatCode=' + data.chatCode + (data.cursorSeqId ? '&cursorSeqId=' + data.cursorSeqId : ''))

export type SyncMessageApiReq = {
  chatCode: string
  lastSeqId: number
}

/**
 * 同步消息（补齐断层/重连同步）
 * 
 * - 后端接口：GET `/message/sync?chatCode=...&lastSeqId=...`
 * - 业务目的：检测到 seqId 不连续时，拉取缺失的消息
 */
export const syncMessageApi = (
  data: SyncMessageApiReq
): Promise<Result<Message[]>> =>
  request.get('/message/sync?chatCode=' + data.chatCode + '&lastSeqId=' + data.lastSeqId)

/**
 * 上传消息附件（如图片）
 *
 * - 后端接口：POST `/message/upload`
 * - 业务目的：上传消息中的图片附件
 * - 对应后端：MessageController.upload
 *
 * @param file 要上传的文件
 * @param onUploadProgress 上传进度回调函数（可选）
 * @returns 上传成功后的图片信息
 */
export const uploadMessageImageApi = (
  file: File,
  onUploadProgress?: (progressEvent: { loaded: number; total?: number }) => void
): Promise<Result<Image>> => {
  const formData = new FormData()
  formData.append('file', file)

  return request.post('/message/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

/**
 * 删除已上传的图片对象（如果后端支持）
 *
 * - 后端接口：DELETE `/message/image?objectName=...`
 * - 业务目的：用户撤回/删除图片资源时释放对象存储空间
 *
 * 注意：当前后端 Controller 未看到该接口实现，若返回 404 说明后端暂未提供。
 */
export const deleteImageApi = (objectName: string): Promise<null> =>
  request.delete('/message/image?objectName=' + objectName)
