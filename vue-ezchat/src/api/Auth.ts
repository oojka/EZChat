import request from '../utils/request'
import {type LoginUser, type RegisterInfo, type Result} from '@/type'

//登录
export const loginApi = (
  username: string,
  password: string
): Promise<Result<LoginUser>> =>
  request.post('/auth/login', { username, password })

export const guestApi = (
  chatCode: string,
  password: string,
  nickName: string,
): Promise<Result<LoginUser>> =>
  request.post('/auth/guest', { chatCode, password, nickName })

export const registerApi = (
  { nickname, username, password, avatar }: RegisterInfo
): Promise<Result<LoginUser>> =>
  request.post('/auth/register', { nickname, username, password, avatar })

