import request from '../utils/request'
import type {ChatRoom, Result} from '@/type'


/**
 * 获取聊天室详情（包含成员、头像、最后消息等）
 *
 * - 后端接口：GET `/chat/{chatCode}`
 * - 业务目的：进入房间时拉取“完整房间信息”，用于渲染右侧成员列表等
 */
export const getChatRoomApi = (chatCode: string): Promise<Result<ChatRoom>> =>
  request.get(`/chat/${chatCode}`)
