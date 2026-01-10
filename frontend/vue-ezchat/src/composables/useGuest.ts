import {reactive, ref} from 'vue'
import {type GuestInfo} from '@/type'
import {guestApi} from '@/api/Auth.ts'
import {ElMessage} from 'element-plus'
import {useRouter} from 'vue-router'

export default function () {

  const dialogVisible = ref(false);

  const router = useRouter();

  const joinChatForm = reactive<GuestInfo>(
    {
      chatCode: '',
      password: '',
      nickName: '',
    }
  )

  const joinAsGuest = async () => {
    const result = await guestApi({ 
      chatCode: joinChatForm.chatCode, 
      password: joinChatForm.password, 
      nickName: joinChatForm.nickName 
    })
    if (result) {
      //提示信息
      ElMessage.success('ゲストとして参加成功しました。')
      const refreshToken = result.data.refreshToken
      if (refreshToken) {
        localStorage.setItem('refreshToken', refreshToken)
        localStorage.removeItem('loginUser')
        localStorage.removeItem('loginGuest')
      }
      // 跳转到首页
      await router.push('/chat')
    }
  }

  const resetJoinChatForm = () => {
    joinChatForm.chatCode = ''
    joinChatForm.password = ''
    joinChatForm.nickName = '';
  }


  return { joinChatForm, joinAsGuest, resetJoinChatForm, dialogVisible }
}
