import request from '@/utils/request'
import type { Result, Friend, FriendRequest, FriendReq } from '@/type'

export const getFriendListApi = (): Promise<Result<Friend[]>> =>
  request.get('/friend/list')

export const getPendingRequestsApi = (): Promise<Result<FriendRequest[]>> =>
  request.get('/friend/requests')

export const sendFriendRequestApi = (data: FriendReq): Promise<Result<void>> =>
  request.post('/friend/request', data)

export const handleFriendRequestApi = (data: FriendReq): Promise<Result<void>> =>
  request.post('/friend/handle', data)

export const removeFriendApi = (friendUid: string): Promise<Result<void>> =>
  request.delete(`/friend/${friendUid}`)

export const updateAliasApi = (data: FriendReq): Promise<Result<void>> =>
  request.put('/friend/alias', data)

export const getOrCreatePrivateChatApi = (data: FriendReq): Promise<Result<string>> =>
  request.post('/friend/chat', data)
