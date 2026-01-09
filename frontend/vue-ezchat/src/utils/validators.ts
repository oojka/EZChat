/**
 * =========================================
 * 密码校验等级说明 (PasswordSecurityLevel)
 * =========================================
 * 1. basic:        基础等级。仅限半角可见字符 (ASCII 33-126)，禁止全角字符和空格。
 * 2. alphanumeric: 中级等级。在 basic 基础上，必须同时包含 [字母] 和 [数字]。
 * 3. strong:       高级等级。在 basic 基础上，必须同时包含 [大写字母]、[小写字母] 和 [数字]。
 * 4. complex:      专家等级。在 basic 基础上，必须同时包含 [大写字母]、[小写字母]、[数字] 和 [特殊符号]。
 */

/**
 * =========================================
 * 核心校验规则 (正则表达式)
 * =========================================
 */

/**
 * 用户名规则：
 * 1. 必须以小写字母开头 ^[a-z]
 * 2. 允许包含小写字母、数字、点(.)、下划线(_)、短横线(-) [a-z0-9._-]
 * 3. 长度限制为 2-20 位 {1,19}$
 */
export const REGEX_USERNAME = /^[a-z][a-z0-9._-]{1,19}$/

/** 昵称规则：2-20 个字符，允许中英文、数字、下划线、短横线、全角符号
 * 
 * 1. 长度限制为 2-20 位 {2,20}$
 * 2. 允许：中文、英文、日文（平/片假名）、韩文、数字、_ - [\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF]
 */
export const REGEX_NICKNAME = /^[A-Za-z0-9_\-\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF]{2,20}$/

/**
 * 用户UID规则：
 * 1. 必须为10位数字
 * 2. 必须为数字
 * 3. 长度限制为 10 位 {10}$
 */
export const REGEX_USER_UID = /^[0-9]{10}$/

/**
 * 通用邀请链接正则
 * 匹配规则：
 * 1. 支持 http 或 https
 * 2. 匹配任意合法域名 (Hostname)
 * 3. 路径必须以 /invite/ 结尾，随后跟着 16-24 位字符
 */
// 通用邀请链接正则 (支持 localhost 和 域名)
export const REGEX_INVITE_URL = /^https?:\/\/[\w.-]+(?::\d+)?\/invite\/([0-9A-Za-z]{16,24})$/
// 正式环境 (Backup)
// export const REGEX_INVITE_URL = /^https:\/\/ez-chat\.oojka\.com\/invite\/([0-9A-Za-z]{16,24})$/

/**
 * 房间ID规则：
 * 1. 必须为8位数字
 * 2. 必须为数字
 * 3. 长度限制为 8 位 {8}$
 */
export const REGEX_CHAT_CODE = /^[0-9]{8}$/

/**
 * 基础密码规则 (半角可见字符)：
 * 匹配 ASCII 33-126 范围内的所有字符，禁止全角和空格
 */
export const PWD_BASE_CHAR = '[\\x21-\\x7e]'

/** 必须包含字母和数字的断言 */
export const LOOKAHEAD_ALPHANUMERIC = '(?=.*[a-zA-Z])(?=.*[0-9])'

/** 必须包含大写、小写、数字的断言 */
export const LOOKAHEAD_STRONG = '(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])'

/** 必须包含大写、小写、数字、特殊符号的断言 */
export const LOOKAHEAD_COMPLEX = '(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[\\x21-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\x7e])'

/**
 * =========================================
 * 类型定义与工具函数
 * =========================================
 */

// 类型导入
import type { LoginUser, JwtPayload, JoinChatCredentialsForm, ValidateChatJoinReq, Image, Message, TextMessage, ImageMessage, MixedMessage, AckPayload } from '@/type'

export type PasswordSecurityLevel = 'basic' | 'alphanumeric' | 'strong' | 'complex'

export type PasswordOptions = {
  min?: number
  max?: number
  level?: PasswordSecurityLevel
}

/**
 * 动态生成密码正则表达式
 *
 * 业务目的：不同页面/不同场景可以配置不同强度（例如注册更强、加入房间可稍弱）。
 *
 * @param options 校验配置 (min, max, level)
 */


export const getPasswordReg = (options: PasswordOptions): RegExp => {
  const { min = 8, max = 20, level = 'basic' } = options

  // 构建长度限制部分
  const lengthConstraint = `${PWD_BASE_CHAR}{${min},${max}}`

  let pattern = '^'
  switch (level) {
    case 'alphanumeric':
      pattern += LOOKAHEAD_ALPHANUMERIC
      break
    case 'strong':
      pattern += LOOKAHEAD_STRONG
      break
    case 'complex':
      pattern += LOOKAHEAD_COMPLEX
      break
    case 'basic':
    default:
      break
  }
  pattern += `${lengthConstraint}$`

  return new RegExp(pattern)
}

/**
 * 校验用户名 (内置非空检查)
 *
 * @param val 输入值
 */
export const isValidUsername = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return REGEX_USERNAME.test(val)
}

/**
 * 校验昵称
 * 
 * @param val 输入值
 */
export const isValidNickname = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return REGEX_NICKNAME.test(val)
}

/**
 * 校验密码 (内置非空检查)
 *
 * @param val 输入值
 * @param options 强度配置
 */
export const isValidPassword = (val: unknown, options?: PasswordOptions): boolean => {
  if (typeof val !== 'string') return false
  return getPasswordReg(options || { min: 8, max: 20, level: 'basic' }).test(val)
}

/**
 * 校验用户UID
 *
 * @param val 输入值
 */
export const isValidUserUid = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return REGEX_USER_UID.test(val)
}

/**
 * 校验房间ID
 *
 * @param val 输入值
 */
export const isValidChatCode = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return REGEX_CHAT_CODE.test(val)
}

/**
 * 校验邀请链接
 *
 * @param val 输入值
 */
export const isValidInviteUrl = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return REGEX_INVITE_URL.test(val)
}

/**
 * =========================================
 * 类型守卫函数 (Type Guards)
 * =========================================
 */

/**
 * 类型守卫：验证数据是否为 LoginUser 类型
 *
 * 业务逻辑：
 * - 检查对象是否包含 uid、username、token 三个必需字段
 * - 所有字段必须为字符串类型
 *
 * @param data 待验证的数据
 * @returns 是否为 LoginUser 类型
 */
export const isLoginUser = (data: unknown): data is LoginUser => {
  if (typeof data !== 'object' || data === null) return false;

  const d = data as Record<string, any>;

  return (
    typeof d.uid === 'string' &&
    typeof d.username === 'string' &&
    typeof d.accessToken === 'string' &&
    typeof d.refreshToken === 'string'
  );
};

/**
 * 类型守卫：验证数据是否为 JwtPayload 类型
 *
 * @param data 待验证的数据
 * @returns 是否为 JwtPayload 类型
 */
export const isJwtPayload = (data: unknown): data is JwtPayload => {
  if (typeof data !== 'object' || data === null) return false

  const d = data as Record<string, unknown>

  return (
    typeof d.uid === 'string' &&
    typeof d.username === 'string' &&
    typeof d.iat === 'number' &&
    typeof d.exp === 'number'
  )
}

/**
 * =========================================
 * 加入聊天室验证函数
 * =========================================
 */

/**
 * 解析并验证加入聊天室信息
 * 
 * 业务逻辑：
 * - 根据 joinMode 验证不同的输入字段
 * - 密码模式：验证 chatCode 和 password
 * - 邀请链接模式：验证 inviteUrl 并提取 inviteCode
 * 
 * @param form 加入聊天室表单数据
 * @returns 验证后的请求参数
 * @throws Error 如果验证失败，抛出包含错误代码的标准 Error
 */
export const parseAndValidateJoinInfo = (form: JoinChatCredentialsForm): ValidateChatJoinReq => {
  const { joinMode, chatCode, password, inviteUrl } = form;

  if (joinMode === 'roomId/password') {
    const code = chatCode?.trim();
    if (!code) {
      throw new Error('ROOM_ID_REQUIRED');
    }
    if (!REGEX_CHAT_CODE.test(code)) {
      throw new Error('INVALID_ROOM_ID_FORMAT');
    }
    if (!password) {
      throw new Error('PASSWORD_REQUIRED');
    }
    return { chatCode: code, password };
  } else {
    const url = inviteUrl?.trim();
    if (!url) {
      throw new Error('INVITE_URL_REQUIRED');
    }

    const match = url.match(REGEX_INVITE_URL);
    // 确保捕获组(group 1)确实抓到了内容
    if (!match || !match[1]) {
      throw new Error('INVALID_INVITE_URL_FORMAT');
    }
    return { inviteCode: match[1] };
  }
};

/**
 * 类型守卫：验证是否为邀请码加入请求
 */
export const isInviteJoinReq = (req: ValidateChatJoinReq): req is { inviteCode: string; chatCode?: never; password?: never } => {
  return 'inviteCode' in req && typeof req.inviteCode === 'string';
}

/**
 * 类型守卫：验证是否为密码加入请求
 */
export const isPasswordJoinReq = (req: ValidateChatJoinReq): req is { chatCode: string; password: string; inviteCode?: never } => {
  return 'chatCode' in req && typeof req.chatCode === 'string' && 'password' in req && typeof req.password === 'string';
}

/**
 * 类型守卫：验证数据是否为 Image 类型
 * 
 * ## 业务逻辑
 * - 检查对象是否包含必需的字段：imageUrl 和 imageThumbUrl
 * - 可选字段：imageName、assetId、blobUrl、blobThumbUrl
 * - 所有字符串字段必须为字符串类型
 * - assetId 如果存在，必须为数字类型
 * 
 * ## 字段说明
 * - imageName: 图片名称（可选）
 * - imageUrl: 原图URL（必需）
 * - imageThumbUrl: 缩略图URL（必需）
 * - assetId: 对象ID，用于关联 objects 表（可选）
 * - blobUrl: 原图 Blob URL（可选，前端持久化字段）
 * - blobThumbUrl: 缩略图 Blob URL（可选，前端持久化字段）
 * 
 * @param data 待验证的数据
 * @returns 是否为 Image 类型
 */
export const isImage = (data: unknown): data is Image => {
  if (typeof data !== 'object' || data === null) {
    return false
  }

  const obj = data as Record<string, unknown>

  // 必需字段：imageUrl 和 imageThumbUrl
  const hasRequiredFields =
    'imageUrl' in obj &&
    'imageThumbUrl' in obj &&
    typeof obj.imageUrl === 'string' &&
    typeof obj.imageThumbUrl === 'string'

  if (!hasRequiredFields) {
    return false
  }

  // 可选字段验证：如果存在，必须符合类型要求
  if ('imageName' in obj && obj.imageName !== undefined && typeof obj.imageName !== 'string') {
    return false
  }

  if ('assetId' in obj && obj.assetId !== undefined && typeof obj.assetId !== 'number') {
    return false
  }

  if ('blobUrl' in obj && obj.blobUrl !== undefined && typeof obj.blobUrl !== 'string') {
    return false
  }

  if ('blobThumbUrl' in obj && obj.blobThumbUrl !== undefined && typeof obj.blobThumbUrl !== 'string') {
    return false
  }

  return true
}

/**
 * 类型守卫：检查消息是否有 images 属性
 * 
 * ## 业务逻辑
 * - 检查 Message 联合类型是否包含 images 属性
 * - 只有 TextMessage、ImageMessage、MixedMessage 有 images 属性
 * - RoomCreatedMessage 和 MemberJoinMessage 没有 images 属性
 * 
 * @param msg 待检查的消息
 * @returns 是否为包含 images 属性的消息类型
 */
export const hasImages = (msg: Message): msg is TextMessage | ImageMessage | MixedMessage => {
  return 'images' in msg
}

/**
 * 类型守卫：验证数据是否为 Message 类型（宽松校验）
 * 
 * ## 业务逻辑
 * - 检查对象是否包含核心字段：sender, chatCode, createTime, type
 * - 这是一个宽松的运行时校验，用于将 WebSocket 的 any 数据转换为 Message
 * 
 * @param data 待验证的数据
 * @returns 是否为 Message 结构
 */
export const isValidMessage = (data: any): data is Message => {
  return (
    !!data &&
    typeof data === 'object' &&
    typeof data.sender === 'string' &&
    typeof data.chatCode === 'string' &&
    typeof data.createTime === 'string' &&
    typeof data.type === 'number' &&
    (data.seqId === undefined || typeof data.seqId === 'number') // Optional check
  )
}

/**
 * 类型守卫：验证数据是否为 AckPayload 类型
 *
 * ## 业务逻辑
 * - 检查对象是否包含 tempId 和 seqId 字段
 * - tempId 必须为字符串类型
 * - seqId 必须为数字类型
 *
 * @param data 待验证的数据
 * @returns 是否为 AckPayload 类型
 */
export const isAckPayload = (data: unknown): data is AckPayload => {
  return (
    !!data &&
    typeof data === 'object' &&
    'tempId' in data &&
    'seqId' in data &&
    typeof (data as Record<string, unknown>).tempId === 'string' &&
    typeof (data as Record<string, unknown>).seqId === 'number'
  )
}

export const isRecord = (value: unknown): value is Record<string, unknown> => {
  return typeof value === 'object' && value !== null
}

export const extractErrorCode = (payload: unknown): string | number | null => {
  if (!isRecord(payload)) return null
  const code = payload.code
  if (typeof code === 'string' || typeof code === 'number') {
    return code
  }
  return null
}
