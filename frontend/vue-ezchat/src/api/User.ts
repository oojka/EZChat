import request from '../utils/request'
import {type LoginUserInfo, type Result} from '@/type'

/**
 * 获取用户信息（昵称/头像/简介等）
 *
 * - 后端接口：GET `/user/{uid}`
 * - 业务目的：用于恢复登录态后补全展示信息（头像、昵称等）
 */
export const getUserInfoApi = (
  uid:string
):Promise<Result<LoginUserInfo>> => request.get(`/user/${uid}`)
