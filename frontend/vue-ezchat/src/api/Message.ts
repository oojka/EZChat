import type {ChatRoom, Message, Result} from '@/type'
import request from '@/utils/request.ts'

/**
 * 拉取消息列表（支持时间戳分页）
 *
 * - 后端接口：GET `/message?chatCode=...&timeStamp=...`
 * - 业务目的：首次进入房间/上拉加载历史时获取消息
 */
export const getMessageListApi =
  (chatCode: string, createTime: string): Promise<Result<{
    chatRoom : ChatRoom
    messageList : Message[]
  }>> =>
  request.get('/message?chatCode=' + chatCode + '&timeStamp=' + createTime)

/**
 * 删除已上传的图片对象（如果后端支持）
 *
 * - 后端接口：DELETE `/message/image?objectName=...`
 * - 业务目的：用户撤回/删除图片资源时释放对象存储空间
 *
 * 注意：当前后端 Controller 未看到该接口实现，若返回 404 说明后端暂未提供。
 */
export const deleteImageApi = (objectName : string): Promise<null> =>
  request.delete('/message/image?objectName=' + objectName)
