import type {ChatRoom, Message, Result} from '@/type'
import request from '@/utils/request.ts'

export const getMessageListApi =
  (chatCode: string, createTime: string): Promise<Result<{
    chatRoom : ChatRoom
    messageList : Message[]
  }>> =>
  request.get('/message?chatCode=' + chatCode + '&timeStamp=' + createTime)

export const deleteImageApi = (objectName : string): Promise<null> =>
  request.delete('/message/image?objectName=' + objectName)
