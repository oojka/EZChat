export type Result<T> = {
  status: 0 | 1
  code: number
  message: string
  data: T
}

export type Image = {
  imageName?: string
  imageUrl: string
  imageThumbUrl: string
  assetId?: number // 对象 ID，用于直接关联 objects 表（可选，向后兼容）
  // 前端持久化字段
  blobUrl?: string    // 原图 Blob URL
  blobThumbUrl?: string // 缩略图 Blob URL
}

export type LoginForm = {
  username: string
  password: string
}

/**
 * 登录用户信息（对应后端 LoginVO）
 * 包括登录用户和访客用户
 * - 用于存储登录态（uid/username/token）
 * - 供 HTTP header 和 WebSocket 连接使用
 */
export type LoginUser = {
  uid: string
  username: string
  token: string
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

export type Token = {
  type: 'formal'
  accessToken?: {
    token: string
    payload: JwtPayload
  }
  refreshToken: {
    token: string
    payload: JwtPayload
  }
}
  | {
    type: 'guest'
    accessToken?: {
      token: string
      payload: JwtPayload
    }
    refreshToken: {
      token: string
      payload: JwtPayload
    }
  }
  | {
    type: 'none'
    accessToken: undefined
    refreshToken: undefined
  }

export interface JwtPayload {
  uid: string;
  username: string;
  iat: number; // 签发时间 (seconds)
  exp: number; // 过期时间 (seconds)
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
 * 消息联合类型 (Discriminated Union)
 * 利用 `type` 字段进行类型收窄
 */
export type Message =
  | TextMessage
  | ImageMessage
  | MixedMessage
  | RoomCreatedMessage
  | MemberJoinMessage
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

export type WebSocketResult = {
  code?: number        // 新增状态码: 1001=Message, 2001=Status, 2002=ACK, 3001=MemberJoin
  isSystemMessage: 0 | 1
  type: string
  data: any
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
