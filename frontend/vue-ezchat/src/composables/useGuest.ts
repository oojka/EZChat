/**
 * 访客加入 Composable（旧版）
 *
 * 核心职责：
 * - 管理访客加入表单（房间号、密码、昵称）
 * - 执行访客登录 API
 * - 处理 Token 存储和页面跳转
 *
 * 注意：此为旧版实现，新流程请使用 useGuestJoin
 *
 * @module useGuest
 * @deprecated 推荐使用 chat/join/useGuestJoin.ts
 */
import {reactive, ref} from 'vue'
import {type GuestInfo} from '@/type'
import {guestApi} from '@/api/Auth.ts'
import {ElMessage} from 'element-plus'
import {useRouter} from 'vue-router'

/**
 * 访客加入业务逻辑 Hook（旧版）
 *
 * @returns 表单数据、加入方法、重置方法
 */
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
