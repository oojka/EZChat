import request from '@/utils/request'
import { type LoginUserInfo, type Result, type RegisterInfo, type LoginUser } from '@/type'

// =====================
// 类型定义
// =====================

/** 更新个人资料请求 */
export type UpdateProfileReq = {
  nickname?: string
  bio?: string
  avatar?: {
    imageName?: string
    imageUrl?: string
    imageThumbUrl?: string
    objectName?: string
    assetId?: number
  }
}

/** 更新密码请求 */
export type UpdatePasswordReq = {
  oldPassword: string
  newPassword: string
}

// =====================
// API 函数
// =====================

/**
 * 获取用户信息（昵称/头像/简介等）
 *
 * - 后端接口：GET `/user/{uid}`
 * - 业务目的：用于恢复登录态后补全展示信息（头像、昵称等）
 */
export const getUserInfoApi = (
  uid: string
): Promise<Result<LoginUserInfo>> => request.get(`/user/${uid}`)

/**
 * 升级为正式用户
 *
 * - 后端接口：POST `/user/upgrade`
 * - 业务目的：将临时访客账号升级为正式账号
 */
export const upgradeUserApi = (
  data: RegisterInfo
): Promise<Result<LoginUser>> =>
  request.post('/user/upgrade', data)

/**
 * 更新个人资料
 *
 * - 后端接口：POST `/user/profile`
 * - 业务目的：更新昵称、简介、头像等非敏感信息
 */
export const updateProfileApi = (
  data: UpdateProfileReq
): Promise<Result<void>> =>
  request.post('/user/profile', data)

/**
 * 修改密码（仅限正式用户）
 *
 * - 后端接口：PUT `/user/password`
 * - 业务目的：验证旧密码后更新密码，成功后需强制重新登录
 */
export const updatePasswordApi = (
  data: UpdatePasswordReq
): Promise<Result<void>> =>
  request.put('/user/password', data)

