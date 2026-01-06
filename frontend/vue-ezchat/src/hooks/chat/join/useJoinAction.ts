import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { isValidNickname, isValidChatCode, isInviteJoinReq, isPasswordJoinReq } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useImageStore } from '@/stores/imageStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import type { Image, GuestJoinReq, LoginForm, JoinChatReq } from '@/type'

/**
 * useJoinAction
 * 职责：管理加入聊天室的具体执行逻辑（访客加入、登录加入、头像处理）
 */
export const useJoinAction = () => {
    const router = useRouter()
    const route = useRoute()
    const { t } = useI18n()

    const userStore = useUserStore()
    const imageStore = useImageStore()
    const appStore = useAppStore()
    const roomStore = useRoomStore()

    // #region State

    /** * 访客信息状态
     * 用于：访客加入页面填写昵称和头像
     */
    const guestNickname = ref('')
    const guestAvatar = ref<Image>({
        objectThumbUrl: '',
        objectUrl: '',
        objectName: '',
        blobUrl: '',
        blobThumbUrl: '',
    })

    /** 默认头像 URL (当用户未上传时显示) */
    const defaultAvatarUrl = ref('')

    /** UI 交互状态 */
    const isLoading = ref(false)

    // #endregion

    // #region Methods

    /** 初始化默认头像（随机生成或获取固定图） */
    const initDefaultAvatarUrl = () => {
        defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
    }

    /** 重置访客信息 */
    const resetGuestForm = () => {
        guestNickname.value = ''
        guestAvatar.value = {
            objectThumbUrl: '',
            objectUrl: '',
            objectName: '',
            blobUrl: '',
            blobThumbUrl: '',
        }
    }

    /** 头像上传成功回调 */
    const handleAvatarSuccess = (response: any) => {
        if (response?.data) {
            guestAvatar.value.objectThumbUrl = response.data.objectThumbUrl || response.data.url || ''
            guestAvatar.value.objectUrl = response.data.objectUrl || response.data.url || ''
        }
    }

    /**
     * 场景A：访客加入逻辑
     * 1. 二次验证房间号/密码一致性
     * 2. 校验昵称头像
     * 3. 处理默认头像上传
     * 4. 调用 store 执行访客加入
     */
    const handleGuestJoin = async (): Promise<boolean> => {
        try {
            // 0. 类型安全获取路由参数
            const currentChatCode = Array.isArray(route.params.chatCode)
                ? route.params.chatCode[0]
                : route.params.chatCode;

            // 1. 验证房间号是否存在
            if (!currentChatCode) {
                throw createAppError(ErrorType.VALIDATION, 'Room ID is missing', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useJoinAction',
                    action: 'handleGuestJoin'
                })
            }

            if (!isValidChatCode(currentChatCode)) {
                throw createAppError(ErrorType.VALIDATION, 'Invalid Room ID format', {
                    severity: ErrorSeverity.WARNING, // 校验失败通常用 WARNING 即可
                    component: 'useJoinAction',
                    action: 'handleGuestJoin'
                })
            }

            // 验证一致性：确保 URL 的房间号与之前 validate 接口返回的一致
            if (currentChatCode !== userStore.validatedChatRoom?.chatCode) {
                throw createAppError(ErrorType.VALIDATION, 'Room code mismatch', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useJoinAction',
                    action: 'handleGuestJoin'
                })
            }

            // 2. 验证昵称
            if (!isValidNickname(guestNickname.value)) {
                throw createAppError(ErrorType.VALIDATION, 'Invalid Nickname format', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useJoinAction',
                    action: 'handleGuestJoin'
                })
            }

            // 3. 头像处理：如果是默认头像且未上传，先上传
            if (!guestAvatar.value.objectUrl && !guestAvatar.value.objectThumbUrl) {
                try {
                    guestAvatar.value = await imageStore.uploadDefaultAvatarIfNeeded(guestAvatar.value, 'user')
                } catch (e) {
                    // 如果是 AppError，则直接抛出
                    if (isAppError(e)) {
                        throw e
                    }

                    // 未知请求错误，抛出异常终止加入流程
                    throw createAppError(
                        ErrorType.NETWORK,
                        'Avatar upload failed',
                        {
                            severity: ErrorSeverity.WARNING,
                            component: 'useJoinAction',
                            action: 'handleGuestJoin',
                            originalError: e as Error,
                        }
                    )
                }
            }

            // 4. 获取并验证凭证，并在 initializeApp 重置状态之前保存 targetCode
            const validatedReq = userStore.validateChatJoinReq
            if (!validatedReq) {
                throw createAppError(
                    ErrorType.VALIDATION,
                    'Validation info expired, please restart',
                    {
                        severity: ErrorSeverity.ERROR,
                        component: 'useJoinAction',
                        action: 'handleGuestJoin'
                    })
            }

            // 在 initializeApp 重置状态之前，先保存 targetCode
            const targetCode = 'chatCode' in validatedReq ? validatedReq.chatCode : currentChatCode
            if (!targetCode) {
                throw createAppError(
                    ErrorType.VALIDATION,
                    'Target chat code is missing',
                    {
                        severity: ErrorSeverity.ERROR,
                        component: 'useJoinAction',
                        action: 'handleGuestJoin'
                    }
                )
            }

            // 5. 构建请求 (使用类型断言或守卫简化逻辑)
            let req: GuestJoinReq | null = null;
            if (isPasswordJoinReq(validatedReq)) {
                // 类型守卫确保这是密码模式
                req = {
                    nickName: guestNickname.value,
                    avatar: guestAvatar.value,
                    chatCode: validatedReq.chatCode,
                    password: validatedReq.password,
                }
            } else if (isInviteJoinReq(validatedReq)) {
                // 类型守卫确保这是邀请码模式
                req = {
                    nickName: guestNickname.value,
                    avatar: guestAvatar.value,
                    inviteCode: validatedReq.inviteCode,
                }
            }

            if (!req) {
                throw createAppError(ErrorType.VALIDATION, 'Invalid validation info', {
                    severity: ErrorSeverity.ERROR,
                    component: 'useJoinAction',
                    action: 'handleGuestJoin'
                })
            }

            // 6. 调用 store 执行访客加入（会调用 initializeApp，重置部分状态）
            const success = await userStore.executeGuestJoin(req, currentChatCode)

            if (success) {
                // 7. 等待 initializeApp 的异步操作完全完成

                // 8. 使用之前保存的 targetCode 跳转到聊天室
                await router.push(`/chat/${targetCode}`)
                return true
            }

            return false
        } catch (e) {
            // 如果是 AppError，则直接抛出
            if (isAppError(e)) {
                throw e
            }
            // 未知错误，抛出异常
            throw createAppError(
                ErrorType.NETWORK,
                t('error.unknown_error'),
                {
                    severity: ErrorSeverity.ERROR,
                    component: 'useJoinAction',
                    action: 'handleGuestJoin',
                    originalError: e
                }
            )
        }
    }

    /**
     * 场景B：登录用户加入逻辑
     * 1. 执行登录并加入
     * 2. 跳转聊天室
     */
    const handleLoginJoin = async (loginForm: LoginForm): Promise<boolean> => {
        try {
            // 1. 验证一致性
            if (!userStore.validatedChatRoom) {
                throw createAppError(
                    ErrorType.VALIDATION,
                    'Validation info lost, please retry',
                    {
                        severity: ErrorSeverity.ERROR,
                        component: 'useJoinAction',
                        action: 'handleLoginJoin'
                    }
                )
            }

            // 2. 在 initializeApp 重置状态之前，先保存目标聊天室代码和加入凭证
            const validatedReq = userStore.validateChatJoinReq

            if (!validatedReq) {
                throw createAppError(
                    ErrorType.VALIDATION,
                    'Validation info expired, please restart',
                    {
                        severity: ErrorSeverity.ERROR,
                        component: 'useJoinAction',
                        action: 'handleLoginJoin'
                    }
                )
            }

            // 3. 执行登录
            const loginUser = await userStore.loginRequest(loginForm.username, loginForm.password)

            if (!loginUser) {
                return false
            }

            // 4. 构建加入请求（利用联合类型的类型守卫）
            let joinReq: JoinChatReq

            if (isInviteJoinReq(validatedReq)) {
                // 邀请码模式
                joinReq = { inviteCode: validatedReq.inviteCode }
            } else if (isPasswordJoinReq(validatedReq)) {
                // 密码模式
                joinReq = { chatCode: validatedReq.chatCode, password: validatedReq.password }
            } else {
                // Should not happen given existing validation
                throw createAppError(ErrorType.VALIDATION, 'Invalid validation info type', { severity: ErrorSeverity.ERROR, component: 'useJoinAction', action: 'handleLoginJoin' })
            }

            // 5. 加入聊天室
            const joinResult = await roomStore.joinChat(joinReq)

            if (joinResult) {
                // 6. 初始化应用状态（会自己管理加载状态）
                await router.push(`/chat/${userStore.validatedChatRoom.chatCode}` || '/chat')
                await appStore.initializeApp(userStore.getAccessToken(), 'login')


                return true
            }

            return false
        } catch (e) {
            // 如果是 AppError，则直接抛出
            if (isAppError(e)) {
                throw e
            }
            // 未知错误，抛出异常
            throw createAppError(
                ErrorType.NETWORK,
                t('error.unknown_error'),
                {
                    severity: ErrorSeverity.ERROR,
                    component: 'useJoinAction',
                    action: 'handleLoginJoin',
                    originalError: e
                }
            )
        }
    }

    // #endregion

    return {
        guestNickname,
        guestAvatar,
        defaultAvatarUrl,
        isLoading,
        initDefaultAvatarUrl,
        resetGuestForm,
        handleAvatarSuccess,
        handleGuestJoin,
        handleLoginJoin
    }
}
