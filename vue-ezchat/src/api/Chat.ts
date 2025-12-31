import request from '../utils/request'
import type {ChatRoom, Result} from '@/type'


export const getChatRoomApi = (chatCode: string): Promise<Result<ChatRoom>> =>
  request.get(`/chat/${chatCode}`)
