import request from '../utils/request'
import {type LoginUserInfo, type Result} from '@/type'

export const getUserInfoApi = (
  uid:string
):Promise<Result<LoginUserInfo>> => request.get(`/user/${uid}`)
