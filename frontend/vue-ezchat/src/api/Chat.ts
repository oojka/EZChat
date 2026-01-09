import request from '@/utils/request'
import type { ChatMember, ChatRoom, CreateChatVO, Image, JoinChatReq, LoginUser, Result } from '@/type'


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

/**
 * 创建聊天室
 *
 * - 后端接口：POST `/chat`
 * - 业务目的：创建房间并返回 chatCode + inviteCode（短链接邀请码）
 */
export const createChatApi = (payload: {
  chatName: string
  avatar: Image
  joinEnable: 0 | 1
  joinLinkExpiryMinutes: number | null
  maxUses?: number
  password?: string
  passwordConfirm?: string
}): Promise<Result<CreateChatVO>> =>
  request.post('/chat', payload)

/**
 * 正式用户加入聊天室
 *
 * - 后端接口：POST `/chat/join`
 * - 业务目的：已登录的正式用户加入指定聊天室，支持密码模式和邀请码模式
 * - 返回：空
 */
export const joinChatApi = (req: JoinChatReq): Promise<Result<null>> =>
  request.post('/chat/join', req)
