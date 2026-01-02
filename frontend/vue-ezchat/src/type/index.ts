export interface Result<T = any> {
  status: 0 | 1
  code: number
  message: string
  data: T
}

export interface Image {
  objectName: string
  objectUrl: string
  objectThumbUrl: string
  objectId?: number // 对象 ID，用于直接关联 objects 表（可选，向后兼容）
  // 前端持久化字段
  blobUrl : string    // 原图 Blob URL
  blobThumbUrl : string // 缩略图 Blob URL
}

export interface LoginInfo {
  username: string
  password: string
}

export interface LoginUser {
  uid: string
  username: string
  token: string
}

export interface LoginUserInfo {
  uid: string
  nickname: string
  avatar: Image
  bio: string
}

export interface GuestInfo {
  chatCode: string;
  password: string;
  nickName: string;
}

export interface RegisterInfo {
  nickname: string
  username: string
  password: string
  confirmPassword: string
  avatar: Image
}

export interface ChatRoom {
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

export interface ChatMember {
  uid: string
  nickname: string
  avatar: Image
  online: boolean
  lastSeenAt: string
}

/**
 * 创建房间返回（后端 CreateChatVO）
 */
export interface CreateChatVO {
  chatCode: string
  inviteCode: string
}

export interface User {
  uid : string
  nickname : string
  avatar : Image
  isOnline: 0 | 1
}

export interface Message {
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

export interface AppInitInfo {
  chatList: ChatRoom[]
  userStatusList: UserStatus[]
}

export interface UserStatus {
  uid: string
  online: boolean
  updateTime: string
}

export interface WebSocketResult {
  isSystemMessage: 0 | 1
  type: string
  data: string
}
