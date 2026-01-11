import type { InternalAxiosRequestConfig } from 'axios'

/**
 * API响应结果类型
 * @template T 数据类型
 */
export type Result<T> = {
  /** 状态：0=成功，1=失败 */
  status: 0 | 1
  /** 错误码 */
  code: number
  /** 消息 */
  message: string
  /** 数据 */
  data: T
}

/**
 * 图片类型
 */
export type Image = {
  /** 图片名称 */
  imageName?: string
  /** 图片URL */
  imageUrl: string
  /** 缩略图URL */
  imageThumbUrl: string
  /** 对象ID，用于直接关联objects表（可选，向后兼容） */
  assetId?: number
  /** 前端持久化字段 - 原图Blob URL */
  blobUrl?: string
  /** 前端持久化字段 - 缩略图Blob URL */
  blobThumbUrl?: string
}

/**
 * 登录表单类型
 */
export type LoginForm = {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
}

/**
 * 登录用户信息（对应后端 LoginVO）
 * 包括登录用户和访客用户
 * - 用于存储登录态（uid/username/accessToken/refreshToken）
 * - 供 HTTP header 和 WebSocket 连接使用
 */
export type LoginUser = {
  /** 用户唯一标识 */
  uid: string
  /** 用户名 */
  username: string
  /** 访问令牌 */
  accessToken: string
  /** 刷新令牌 */
  refreshToken: string
}

/**
 * 用户登录状态（使用 discriminated union 确保类型安全）
 *
 * 业务逻辑：
 * - formal 和 guest 互斥，只能有一个有值
 * - 使用联合类型确保编译时类型安全
 * - 通过 type 字段明确当前状态
 */
export type UserLoginState =
  | { type: 'formal'; formal: LoginUser; guest: undefined }
  | { type: 'guest'; formal: undefined; guest: LoginUser }
  | { type: 'none'; formal: undefined; guest: undefined }

/**
 * 令牌负载类型
 */
export type TokenPayload = {
  /** 令牌字符串 */
  token: string
  /** JWT负载 */
  payload: JwtPayload
}

/**
 * 令牌类型
 */
export type Token = {
  /** 令牌类型：正式用户、访客或无 */
  type: 'formal' | 'guest' | 'none'
  /** 访问令牌 */
  accessToken?: TokenPayload
  /** 刷新令牌 */
  refreshToken?: TokenPayload
}

/**
 * JWT负载接口
 */
export interface JwtPayload {
  /** 用户唯一标识 */
  uid: string;
  /** 用户名 */
  username: string;
  /** 签发时间 (秒) */
  iat: number;
  /** 过期时间 (秒) */
  exp: number;
}
/**
 * 登录用户详细信息（对应后端 UserVO）
 *
 * - 用于 UI 展示（昵称/头像/简介）
 * - 通过 GET /user/{uid} 接口获取
 */
export type LoginUserInfo = {
  uid: string
  nickname: string
  avatar: Image
  bio: string
  userType: 'formal' | 'guest' // 用户类型：正式用户或访客
}

export type GuestInfo = {
  chatCode: string;
  password: string;
  nickName: string;
}

export type RegisterInfo = {
  nickname: string
  username: string
  password: string
  confirmPassword: string
  avatar: Image
  bio?: string
}

export type ChatRoom = {
  chatCode: string //
  chatName: string //
  ownerUid: string //
  joinEnabled: number //
  passwordEnabled?: number
  maxMembers: number
  announcement?: string | null
  lastActiveAt: string //
  createTime: string //
  updateTime: string //
  unreadCount: number //
  onLineMemberCount: number
  memberCount: number
  avatar: Image //
  lastMessage: Message
  chatMembers: ChatMember[]
}

export type ChatMember = {
  uid: string
  chatCode: string // 补齐：后端 ChatMemberVO 已包含 chatCode
  nickname: string
  avatar: Image
  online: boolean
  lastSeenAt: string
}

export type ChatInvite = {
  id: number
  inviteCode: string
  expiresAt: string
  maxUses: number
  usedCount: number
  createTime: string
}

export type ChatInviteCreateReq = {
  joinLinkExpiryMinutes: number
  maxUses: 0 | 1
}

export type ChatBasicUpdateReq = {
  chatName: string
  maxMembers: number
  announcement?: string | null
  avatar: Image
}

export type ChatKickReq = {
  memberUids: string[]
}

export type ChatOwnerTransferReq = {
  newOwnerUid: string
}

export type ChatPasswordUpdateReq = {
  joinEnableByPassword: 0 | 1
  password: string
  passwordConfirm: string
}

/**
 * 创建房间返回（后端 CreateChatVO）
 */
export type CreateChatVO = {
  chatCode: string
  inviteCode: string
}

/**
 * 验证聊天室加入请求 (Strict Union Version)
 * * 利用联合类型强制执行业务逻辑：
 * 1. ByPassword: 必须同时包含 chatCode 和 password
 * 2. ByInvite: 必须包含 inviteCode
 */
export type ValidateChatJoinReq =
  | {
    /** 模式1：密码验证 - chatCode 必填 */
    chatCode: string
    /** 模式1：密码验证 - password 必填 (与 chatCode 强绑定) */
    password: string
    /** 模式1下，严禁出现 inviteCode */
    inviteCode?: never
  }
  | {
    /** 模式2：邀请码验证 - inviteCode 必填 */
    inviteCode: string
    /** 模式2下，严禁出现 chatCode */
    chatCode?: never
    /** 模式2下，严禁出现 password */
    password?: never
  }

/**
 * 基础消息接口 (Base Message)
 */
interface BaseMessage {
  sender: string
  chatCode: string
  seqId?: number // 群内消息序号 (可选，本地临时消息无此字段)
  createTime: string
  tempId: string | null
  status: 'sending' | 'sent' | 'error' | null
}

/**
 * Type 0: 文本消息
 */
export interface TextMessage extends BaseMessage {
  type: 0
  text: string
  images: never[] // 明确为空
}

/**
 * Type 1: 图片消息
 */
export interface ImageMessage extends BaseMessage {
  type: 1
  text: string // 可能为空或作为描述
  images: Image[]
}

/**
 * Type 2: 混合消息
 */
export interface MixedMessage extends BaseMessage {
  type: 2
  text: string
  images: Image[]
}

/**
 * 输入内容类型（只包含用户可输入的消息类型）
 * 用于输入框组件，确保类型安全
 */
export type InputMessage = TextMessage | ImageMessage | MixedMessage

/**
 * Type 10: 房间创建系统消息
 */
export interface RoomCreatedMessage extends BaseMessage {
  type: 10
  text: string | null // 可能为 null，由前端本地化
}

/**
 * Type 11: 成员加入通知 (含 Member 信息的广播)
 */
// Type 11: 成员加入通知 (不再强制携带 member，member 通过 code=3001 独立处理)
export interface MemberJoinMessage extends BaseMessage {
  type: 11
  text: string
  // member: ChatMember // [REMOVED] 解耦: Member信息通过 WebSocket code=3001 独立下发，不耦合在消息体中
}

/**
 * Type 13: 成员被移除系统消息
 */
export interface MemberRemovedMessage extends BaseMessage {
  type: 13
  text: string
}

/**
 * 消息联合类型 (Discriminated Union)
 * 利用 `type` 字段进行类型收窄
 */
export type Message =
  | TextMessage
  | ImageMessage
  | MixedMessage
  | RoomCreatedMessage
  | MemberJoinMessage
  | MemberRemovedMessage
  // 兜底类型
  | (BaseMessage & { type: number; text?: string; images?: Image[]; member?: any })

export type JoinBroadcastMessage = MemberJoinMessage // 兼容别名


export type AppInitInfo = {
  chatList: ChatRoom[]
  userStatusList: UserStatus[]
}

export type UserStatus = {
  uid: string
  online: boolean
  updateTime: string
}

export type MemberLeaveBroadcastPayload = {
  chatCode: string
  uid: string
  nickname: string
  leftAt: string
}

export type MemberRemovedBroadcastPayload = {
  chatCode: string
  removedUid: string
  removedNickname: string
  operatorUid: string
  operatorNickname: string
  removedAt: string
}

export type OwnerTransferBroadcastPayload = {
  chatCode: string
  oldOwnerUid: string
  newOwnerUid: string
  newOwnerNickname: string
  transferredAt: string
}

export type RoomDisbandBroadcastPayload = {
  chatCode: string
  operatorUid: string
  operatorNickname: string
  disbandAt: string
}

export type ForceLogoutPayload = {
  uid: string
  reason: string
  forcedAt: string
}

export type WebSocketResult = {
  code?: number        // 新增状态码: 1001=Message, 2001=Status, 2002=ACK, 2003=ForceLogout, 3001=MemberJoin, 3002=MemberLeave, 3003=OwnerTransfer, 3004=RoomDisband, 3005=MemberRemoved
  isSystemMessage: 0 | 1
  type: string
  data: unknown
}

/**
 * WebSocket ACK 消息载荷（对应后端 AckVO）
 *
 * 业务场景：服务端收到消息后回传确认，前端据此更新消息状态
 * - tempId: 客户端发送时生成的临时ID，用于匹配本地消息
 * - seqId: 服务端持久化后分配的序列号
 */
export type AckPayload = {
  tempId: string
  seqId: number
}

export type GuestJoinReq = {
  chatCode: string
  password: string
  nickName: string
  avatar: Image
} | {
  inviteCode: string
  nickName: string
  avatar: Image
}

export type JoinChatReq = {
  chatCode: string
  password: string
} | {
  inviteCode: string
}

export type JoinChatCredentialsForm = {
  /** 加入模式：'password' (密码/房间号) 或 'invite' (邀请链接) */
  joinMode: 'roomId/password' | 'inviteUrl'
  /** 聊天室代码 (8位数字) */
  chatCode: string
  /** 房间密码 */
  password: string
  /** 完整邀请链接 URL */
  inviteUrl: string
  /** 解析出的邀请码 */
  inviteCode: string
}

/**
 * 可重试的请求配置扩展
 */
export type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean       // 标记是否已经重试过，防止死循环
  _retryToken?: string   // 记录重试时使用的 token
}

/**
 * 业务错误码的联合类型（字符串或数字）
 */
export type ErrorCodeValue = string | number

// ==================== Friend System Types ====================

export type Friend = {
  userId: number
  uid: string
  nickname: string
  alias?: string
  avatar?: Image
  bio?: string
  online: boolean
  lastSeenAt: string
  createTime: string
}

export type FriendRequest = {
  id: number
  senderId: number
  senderUid: string
  senderNickname: string
  senderAvatar?: Image
  status: number // 0=Pending, 1=Accepted, 2=Rejected
  createTime: string
}

export type FriendReq = {
  targetUid?: string
  requestId?: number
  accept?: boolean
  friendUid?: string
  alias?: string
}
