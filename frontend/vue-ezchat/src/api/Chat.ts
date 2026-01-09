import request from '@/utils/request'
import type { ChatInvite, ChatInviteCreateReq, ChatMember, ChatRoom, ChatPasswordUpdateReq, CreateChatVO, Image, JoinChatReq, LoginUser, Result } from '@/type'


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

/**
 * 退出聊天室
 *
 * - 后端接口：POST `/chat/{chatCode}/leave`
 */
export const leaveChatApi = (chatCode: string): Promise<Result<null>> =>
  request.post(`/chat/${chatCode}/leave`)

/**
 * 解散聊天室（群主）
 *
 * - 后端接口：POST `/chat/{chatCode}/disband`
 */
export const disbandChatApi = (chatCode: string): Promise<Result<null>> =>
  request.post(`/chat/${chatCode}/disband`)

/**
 * 更新聊天室密码（群主）
 *
 * - 后端接口：POST `/chat/{chatCode}/password`
 */
export const updateChatPasswordApi = (chatCode: string, payload: ChatPasswordUpdateReq): Promise<Result<null>> =>
  request.post(`/chat/${chatCode}/password`, payload)

/**
 * 获取聊天室有效邀请链接列表（群主）
 *
 * - 后端接口：GET `/chat/{chatCode}/invites`
 */
export const getChatInvitesApi = (chatCode: string): Promise<Result<ChatInvite[]>> =>
  request.get(`/chat/${chatCode}/invites`)

/**
 * 创建新的邀请链接（群主）
 *
 * - 后端接口：POST `/chat/{chatCode}/invites`
 */
export const createChatInviteApi = (chatCode: string, payload: ChatInviteCreateReq): Promise<Result<ChatInvite>> =>
  request.post(`/chat/${chatCode}/invites`, payload)

/**
 * 撤销邀请链接（群主）
 *
 * - 后端接口：DELETE `/chat/{chatCode}/invites/{inviteId}`
 */
export const revokeChatInviteApi = (chatCode: string, inviteId: number): Promise<Result<null>> =>
  request.delete(`/chat/${chatCode}/invites/${inviteId}`)
