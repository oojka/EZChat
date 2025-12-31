import request from '@/utils/request'
import {type AppInitInfo, type Result} from '@/type'

export const initApi =
  (): Promise<Result<AppInitInfo>> => request.get('/init')
