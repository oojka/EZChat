export type Result<T> = {
  status: 0 | 1
  code: number
  message: string
  data: T
}

export type Image = {
  objectName? : string
  objectUrl : string
  objectThumbUrl : string
  objectId?: number // 对象 ID，用于直接关联 objects 表（可选，向后兼容）
  // 前端持久化字段
  blobUrl? : string    // 原图 Blob URL
  blobThumbUrl? : string // 缩略图 Blob URL
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
}

export type ChatRoom = {
  chatCode : string //
  chatName : string //
  ownerUid : string //
  joinEnabled: number //
  lastActiveAt: string //
  createTime : string //
  updateTime : string //
  unreadCount : number //
  onLineMemberCount : number
  memberCount : number
  avatar : Image //
  lastMessage : Message
  chatMembers : ChatMember[]
}

export type ChatMember = {
  uid: string
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

export type Message = {
  sender: string
  chatCode: string
  /**
   * 0: Text, 1: Image, 2: Mixed
   */
  type: number
  text: string
  images: Image[]
  createTime: string
  tempId : string | null
  status : 'sending' | 'sent' | 'error' | null
}

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
  isSystemMessage: 0 | 1
  type: string
  data: string
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

/** 房间基础信息结构 */
export type RoomInfo = {
  chatCode: string
  chatName: string
  memberCount: number
  avatar: {
    objectThumbUrl: string
    objectUrl: string
  }
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
