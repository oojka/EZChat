import {ref} from 'vue'

export const useJoinChat = () => {
  const joinChatForm = ref<{
    inviteUrl: string
    chatCode: string
    password: string
    joinMode: string
  }>({
    inviteUrl: '',
    chatCode: '',
    password: '',
    joinMode: 'id',
  })

  /**
   * 重置加入房间表单
   */
  const resetJoinForm = () => {
    joinChatForm.value = {
      inviteUrl: '',
      chatCode: '',
      password: '',
      joinMode: 'id',
    }
  }

  // 提交逻辑
  const handleJoin = () => {
    console.log('[INFO] [JoinChatHook] Join logic:', joinChatForm.value)
    // TODO: 调用加入接口
  }

  return {
    joinChatForm,
    handleJoin,
    resetJoinForm,
  }
}
