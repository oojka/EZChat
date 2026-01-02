import request from '../utils/request'
import type {ChatMember, ChatRoom, Result} from '@/type'


/**
 * 获取聊天室详情（包含成员、头像、最后消息等）
 *
 * - 后端接口：GET `/chat/{chatCode}`
 * - 业务目的：进入房间时拉取“完整房间信息”，用于渲染右侧成员列表等
 */
export const getChatRoomApi = (chatCode: string): Promise<Result<ChatRoom>> =>
  request.get(`/chat/${chatCode}`)

/**
 * 获取聊天室成员列表（按 chatCode 懒加载）
 *
 * - 后端接口：GET `/chat/{chatCode}/members`
 * - 业务目的：右侧成员栏按需拉取，避免 refresh 初始化阶段全量加载所有群成员
 */
export const getChatMembersApi = (chatCode: string): Promise<Result<ChatMember[]>> =>
  request.get(`/chat/${chatCode}/members`)
