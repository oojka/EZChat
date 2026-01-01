import request from '../utils/request'
import {type LoginUser, type RegisterInfo, type Result} from '@/type'

/**
 * 用户登录
 *
 * - 后端接口：POST `/auth/login`
 * - 业务目的：获取登录态（uid/username/token），供后续 HTTP Header 与 WS 连接使用
 */
export const loginApi = (
  username: string,
  password: string
): Promise<Result<LoginUser>> =>
  request.post('/auth/login', { username, password })

/**
 * 访客加入聊天
 *
 * - 后端接口：POST `/auth/guest`
 * - 业务目的：无需注册即可进入指定 chatCode（通常用于临时体验）
 */
export const guestApi = (
  chatCode: string,
  password: string,
  nickName: string,
): Promise<Result<LoginUser>> =>
  request.post('/auth/guest', { chatCode, password, nickName })

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

