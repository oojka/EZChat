
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { useUserStore } from '@/stores/userStore'
import { useRoomStore } from '@/stores/roomStore'
import { useAppStore } from '@/stores/appStore'
import { useJoinInput } from '@/hooks/chat/join/useJoinInput'
import { isValidInviteUrl } from '@/utils/validators'
import { isAppError } from '@/error/ErrorTypes'
import type { JoinChatCredentialsForm, JoinChatReq } from '@/type'

const { t } = i18n.global

/**
 * 处理邀请路由的核心业务逻辑
 * 
 * 职责：
 * 1. 验证 URL 和 InviteCode
 * 2. 调用 API 验证邀请有效性
 * 3. 尝试恢复用户登录态或自动加入
 * 4. 决定最终跳转的目标路由
 * 
 * @param to 目标路由对象
 * @returns 跳转目标路由配置
 */
export const processInviteRoute = async (to: RouteLocationNormalized): Promise<RouteLocationRaw> => {
    const userStore = useUserStore()
    const roomStore = useRoomStore()
    const appStore = useAppStore()

    // 直接从当前 URL 获取完整邀请链接并验证
    // 注意：此时 window.location.href 还是旧的 URL（如果是首次加载则是目标 URL）
    // 如果进入此路由，说明 URL 匹配 /invite/:code
    const inviteUrl = window.location.href

    // 从 URL 中提取 inviteCode (路由参数)
    const inviteCodeParam = to.params.inviteCode
    if (typeof inviteCodeParam !== 'string' || !inviteCodeParam) {
        return { name: 'NotFound', replace: true }
    }

    // 如果 inviteUrl 校验失败（例如非标准域名），但 param 存在
    // 我们可以尝试构造一个标准 URL 或仅依赖 code (兼容开发环境 localhost 等)
    const validUrl = isValidInviteUrl(inviteUrl)
        ? inviteUrl
        : `https://ez-chat.oojka.com/invite/${inviteCodeParam}`

    const joinCredentialsReq: JoinChatCredentialsForm = {
        joinMode: 'inviteUrl',
        inviteUrl: validUrl,
        chatCode: '',
        password: '',
        inviteCode: inviteCodeParam,
    }

    try {
        // 使用 useJoinInput 的 validateAndGetPayload 方法进行验证
        // 这一步会把验证结果存入 userStore.validatedChatRoom
        await useJoinInput().validateAndGetPayload(joinCredentialsReq, false)

        // 如果验证结果为空，则跳转到错误页
        if (!userStore.validatedChatRoom?.chatCode) {
            return { name: 'NotFound', replace: true }
        }

        // 如果内存中没有 token，则尝试恢复登录信息
        if (!userStore.hasToken()) {
            userStore.restoreLoginUserFromStorage() || userStore.restoreLoginGuestFromStorage();
        }

        // 如果有 token，检查是否为正式用户并尝试直接加入
        if (userStore.hasToken()) {
            // 使用同步的 isFormalUser 状态判断，避免 initLoginUserInfo 尚未完成导致判断失效
            if (!userStore.isFormalUser) {
                // 如果不是正式用户（或者状态尚未恢复），尝试恢复一下（此时 init 也只会恢复 memory state）
                await userStore.initLoginUserInfo()
            }

            // 只有正式用户才自动加入
            if (userStore.isFormalUser) {
                const joinChatReq: JoinChatReq = {
                    inviteCode: inviteCodeParam,
                }
                const result = await roomStore.joinChat(joinChatReq)
                const targetChatCode = userStore.validatedChatRoom.chatCode

                if (result === 'SUCCESS' || result === 'ALREADY_JOINED') {
                    // 加入成功或已加入，显示成功提示并跳转
                    if (result === 'SUCCESS') {
                        // 仅首次加入才提示，避免刷新页面时重复提示
                        ElMessage.success(t('api.join_chat_success') || 'Joined successfully')
                        // 重新初始化 App 状态以拉取最新会话列表
                        await appStore.initializeApp(userStore.getAccessToken(), 'login')
                    }
                    return { name: 'ChatRoom', params: { chatCode: targetChatCode }, replace: true }
                }
            }
        }

        // 其他情况（未登录、访客、或加入失败需手动确认），跳转到 JoinView 页面让用户操作
        return { name: 'Join', params: { chatCode: userStore.validatedChatRoom.chatCode }, replace: true }

    } catch (error) {
        if (isAppError(error)) {
            // 业务错误通常意味着验证失败
            console.warn('Invite validation failed:', error);
            return { name: 'NotFound', replace: true }
        }
        console.error('Unknown error during invite validation:', error);
        return { name: 'Error', replace: true }
    }
}
