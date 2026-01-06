import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { isInviteJoinReq, isPasswordJoinReq } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import type { LoginForm, JoinChatReq } from '@/type'
import { ElMessage } from 'element-plus'

/**
 * useLoginJoin
 * 职责：管理登录用户/登录后加入流程
 */
export const useLoginJoin = () => {
    const { t } = useI18n()
    const router = useRouter()
    const userStore = useUserStore()
    const roomStore = useRoomStore()
    const appStore = useAppStore()

    const isLoading = ref(false)

    /**
     * 场景A：在加入页面，用户未登录 -> 填写账号密码 -> 登录 -> 加入
     */
    const handleLoginAndJoin = async (loginForm: LoginForm, shouldNavigate = true): Promise<boolean> => {
        isLoading.value = true
        try {
            // 1. 检查是否存在校验过的房间信息
            if (!userStore.validatedChatRoom || !userStore.validateChatJoinReq) {
                throw createAppError(ErrorType.VALIDATION, 'Validation info lost, please retry', { severity: ErrorSeverity.ERROR, component: 'useLoginJoin', action: 'handleLoginAndJoin' })
            }

            // 2. 执行登录
            const loginUser = await userStore.loginRequest(loginForm.username, loginForm.password)
            if (!loginUser) return false

            // 3. 构建加入请求
            const validatedReq = userStore.validateChatJoinReq
            let joinReq: JoinChatReq
            if (isInviteJoinReq(validatedReq)) {
                joinReq = { inviteCode: validatedReq.inviteCode }
            } else if (isPasswordJoinReq(validatedReq)) {
                joinReq = { chatCode: validatedReq.chatCode, password: validatedReq.password }
            } else {
                throw createAppError(ErrorType.VALIDATION, 'Invalid validation info type', { severity: ErrorSeverity.ERROR, component: 'useLoginJoin', action: 'handleLoginAndJoin' })
            }

            // 4. 加入房间
            const result = await roomStore.joinChat(joinReq)

            if (result === 'SUCCESS' || result === 'ALREADY_JOINED') {
                // 5. 初始化 App (登录后必须)
                const targetCode = userStore.validatedChatRoom.chatCode

                // 这里需要等待 App 初始化，因为登录刚成功
                // AppStore.initializeApp 会拉取房间列表等
                await appStore.initializeApp(userStore.getAccessToken(), 'login')

                // 6. 跳转
                // 6. 跳转
                if (shouldNavigate) {
                    await roomStore.processJoinNavigation(targetCode)
                }
                return true
            }

            return false

        } catch (e) {
            const msg = isAppError(e) ? e.message : (e instanceof Error ? e.message : 'Login/Join failed')
            ElMessage.error(msg)
            return false
        } finally {
            isLoading.value = false
        }
    }


    /**
     * 场景B：当前已登录用户执行加入 (例如在 Dialog 或 Quick Access)
     * 假设：已经有了 JoinChatReq (从 Input 模块转换而来)
     */
    const executeJoin = async (req: JoinChatReq, shouldNavigate = true): Promise<'SUCCESS' | 'ALREADY_JOINED' | 'FAILED'> => {
        isLoading.value = true
        try {
            const result = await roomStore.joinChat(req)

            if (result === 'SUCCESS' || result === 'ALREADY_JOINED') {
                // 获取目标 code 以便跳转
                // 如果是 inviteCode，joinChat 内部可能已经刷新了 roomList
                // 我们可以尝试从 validated info 或者 response (joinChat doesn't return room info currently, but roomStore.joinChat refreshes list)

                // 如果是 ALREADY_JOINED，roomStore会弹窗，但我们需要处理跳转
                // 这里需要知道 chatCode。
                // 如果 req 是 { chatCode } 很容易。
                // 如果 req 是 { inviteCode }，我们需要知道对应的 chatCode。

                // 通常调用此方法前，Input 模块应该已经 validate 过了，userStore.validatedChatRoom 应该有值。
                const targetCode = userStore.validatedChatRoom?.chatCode || ('chatCode' in req ? req.chatCode : '')

                if (shouldNavigate) {
                    await roomStore.processJoinNavigation(targetCode)
                }
                return result
            }

            return 'FAILED'

        } catch (e) {
            const msg = isAppError(e) ? e.message : (e instanceof Error ? e.message : 'Join failed')
            ElMessage.error(msg)
            return 'FAILED'
        } finally {
            isLoading.value = false
        }
    }

    return {
        isLoading,
        handleLoginAndJoin,
        executeJoin
    }
}
