import axios from 'axios'
import request from '@/utils/request'
import type { LoginUser, RegisterInfo, Result, ValidateChatJoinReq, ChatRoom, GuestJoinReq, LoginForm, Image } from '@/type'

/**
 * 用户登录
 *
 * - 后端接口：POST `/auth/login`
 * - 业务目的：获取登录态（uid/username/token），供后续 HTTP Header 与 WS 连接使用
 */
export const loginApi = (
  data: LoginForm
): Promise<Result<LoginUser>> =>
  request.post('/auth/login', data)

export type GuestApiReq = {
  chatCode: string
  password: string
  nickName: string
}

/**
 * 访客加入聊天
 *
 * - 后端接口：POST `/auth/guest`
 * - 业务目的：无需注册即可进入指定 chatCode（通常用于临时体验）
 */
export const guestApi = (
  data: GuestApiReq
): Promise<Result<LoginUser>> =>
  request.post('/auth/guest', data)

/**
 * 正式用户注册
 *
 * - 后端接口：POST `/auth/register`
 * - 业务目的：创建账号并返回登录态（含 token）
 */
export const registerApi = (
  { nickname, username, password, avatar }: RegisterInfo
): Promise<Result<LoginUser>> =>
  request.post('/auth/register', { nickname, username, password, avatar })

/**
 * 验证聊天室加入请求
 *
 * - 后端接口：POST `/auth/validate-join`
 * - 业务目的：轻量级验证接口，仅验证房间是否存在、密码是否正确、是否允许加入
 * - 注意：此接口必须放在 `/auth` 路径下，以跳过 token 检查
 *
 * @param req 验证请求对象（包含 chatCode + password 或 inviteCode）
 * @returns 简化的 ChatRoom（仅包含 chatCode, chatName, avatar, memberCount）
 */
export const validateChatJoinApi = (
  req: ValidateChatJoinReq
): Promise<Result<ChatRoom>> =>
  request.post('/auth/validate', req)

/**
 * 加入聊天室（访客或邀请码模式）
 *
 * - 后端接口：POST `/auth/join`
 * - 业务目的：正式加入聊天室，获取登录态（uid/username/token）
 * - TODO: 后端 API 暂未实现
 */
export const guestJoinApi = (
  data: GuestJoinReq
): Promise<Result<LoginUser>> =>
  request.post('/auth/join', data)

/**
 * 上传头像
 *
 * - 后端接口：POST `/auth/register/upload`
 * - 业务目的：上传用户头像（用于注册或更新头像）
 * - 对应后端：AuthController.upload
 *
 * @param file 要上传的头像文件
 * @param onUploadProgress 上传进度回调函数（可选）
 * @returns 上传成功后的图片信息
 */
export const uploadAvatarApi = (
  file: File,
  onUploadProgress?: (progressEvent: { loaded: number; total?: number }) => void
): Promise<Result<Image>> => {
  const formData = new FormData()
  formData.append('file', file)

  return request.post('/auth/register/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress
  })
}

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 600000
})

/**
 * RefreshToken 兑换 AccessToken
 *
 * - 后端接口：POST `/auth/refresh`
 * - 业务目的：刷新 accessToken，保持登录态
 */
export const refreshTokenApi = (
  refreshToken: string
): Promise<Result<LoginUser>> =>
  refreshClient
    .post('/auth/refresh', { refreshToken })
    .then(response => response.data)
