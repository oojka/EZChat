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
    const result = await guestApi(joinChatForm.chatCode, joinChatForm.password,joinChatForm.nickName)
    if (result) {
      //提示信息
      ElMessage.success('ゲストとして参加成功しました。')
      // 保存token到本地存储
      localStorage.setItem('loginUser', JSON.stringify(result.data))
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
