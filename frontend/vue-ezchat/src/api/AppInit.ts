import request from '@/utils/request'
import {type AppInitInfo, type Result} from '@/type'

/**
 * 获取应用初始化数据（房间列表、在线状态列表等）
 *
 * - 后端接口：GET `/init`
 * - 业务目的：用于刷新后恢复“房间列表 + 在线状态”，避免页面空白
 */
export const initApi = (): Promise<Result<AppInitInfo>> => request.get('/init')
