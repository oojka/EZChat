import request from '../utils/request'
import {type LoginUserInfo, type Result} from '@/type'

export const getUserInfoApi = (
  uId:string
):Promise<Result<LoginUserInfo>> => request.get(`/user/${uId}`)
