import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { REGEX_CHAT_CODE, REGEX_INVITE_URL, getPasswordReg, parseAndValidateJoinInfo } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import type { JoinChatCredentialsForm, ValidateChatJoinReq } from '@/type'

/**
 * useJoinForm
 * 职责：管理加入聊天室的凭证表单、验证规则及首步验证逻辑
 */
export const useJoinForm = () => {
    const router = useRouter()
    const { t } = useI18n()

    const userStore = useUserStore()
    const appStore = useAppStore()
    const roomStore = useRoomStore()

    // #region State

    /** * 主流程表单状态
     * 用于：首页加入区域对邀请信息(type: ValidateChatJoinReq)进行验证、路由跳转携带参数
     */
    const joinChatCredentialsForm = ref<JoinChatCredentialsForm>({
        joinMode: 'roomId/password',
        chatCode: '',
        password: '',
        inviteUrl: '',
        inviteCode: '',
    })

    /** 对话框表单 Ref (用于 Element Plus 验证) */
    const joinChatCredentialsFormRef = ref()

    /** 验证中状态 */
    const isValidating = ref(false)

    // #endregion

    // #region Computed

    const isRoomIdPasswordMode = computed(() => {
        return joinChatCredentialsForm.value.joinMode === 'roomId/password'
    })

    const isInviteUrlMode = computed(() => {
        return joinChatCredentialsForm.value.joinMode === 'inviteUrl'
    })

    /** * 包装 joinMode 以支持 v-model 双向绑定
     * 并在切换时处理副作用(如果需要)
     */
    const joinMode = computed({
        get: () => joinChatCredentialsForm.value.joinMode,
        set: (val: 'roomId/password' | 'inviteUrl') => {
            joinChatCredentialsForm.value.joinMode = val
            // 切换模式时重置表单是个好习惯，防止数据混淆
            resetJoinForm()
        }
    })

    /** Element Plus 表单验证规则 */
    const joinChatCredentialsFormRules = computed(() => {
        const rules: any = {}
        if (isRoomIdPasswordMode.value) {
            // ID + 密码模式
            rules.chatCode = [
                // 请输入房间号。
                { required: true, message: t('validation.chat_code_required') || 'Room ID is required', trigger: 'blur' },
                // 不合法的房间号格式。
                { pattern: REGEX_CHAT_CODE, message: t('validation.chat_code_format') || 'Invalid Room ID format', trigger: 'blur' }
            ]
            rules.password = [
                // 请输入密码。
                { required: true, message: t('validation.password_required') || 'Password is required', trigger: 'blur' },
                {
                    pattern: getPasswordReg({ min: 8, max: 20, level: 'basic' }),
                    // 请输入 8-20 位半角英数字及特殊符号。
                    message: t('validation.password_format') || 'Invalid password format',
                    trigger: 'blur'
                }
            ]
        } else {
            // 邀请链接模式
            rules.inviteUrl = [
                // 请输入邀请链接。
                { required: true, message: t('validation.invite_url_required') || 'Invite URL is required', trigger: 'blur' },
                // 不合法的邀请链接格式。
                { pattern: REGEX_INVITE_URL, message: t('validation.invite_url_format') || 'Invalid Invite URL format', trigger: 'blur' }
            ]
        }

        return rules
    })

    // #endregion

    // #region Methods

    /** 重置主表单数据 */
    const resetJoinForm = () => {
        joinChatCredentialsForm.value = {
            joinMode: 'roomId/password', // 默认回退到密码模式
            chatCode: '',
            password: '',
            inviteUrl: '',
            inviteCode: '',
        }
    }

    /** 手动切换加入模式 */
    const toggleJoinMode = () => {
        const newMode = joinChatCredentialsForm.value.joinMode === 'roomId/password' ? 'inviteUrl' : 'roomId/password'
        // 先重置表单字段
        joinChatCredentialsForm.value.chatCode = ''
        joinChatCredentialsForm.value.password = ''
        joinChatCredentialsForm.value.inviteUrl = ''
        joinChatCredentialsForm.value.inviteCode = ''
        // 最后设置模式，确保模式正确应用
        joinChatCredentialsForm.value.joinMode = newMode
    }

    /** 设置加入模式（接受参数） */
    const changeJoinMode = (mode: 'roomId/password' | 'inviteUrl') => {
        // 先重置表单字段，但保留要设置的模式
        joinChatCredentialsForm.value.chatCode = ''
        joinChatCredentialsForm.value.password = ''
        joinChatCredentialsForm.value.inviteUrl = ''
        joinChatCredentialsForm.value.inviteCode = ''
        // 最后设置模式，确保模式正确应用
        joinChatCredentialsForm.value.joinMode = mode
    }

    /**
     * 构建并验证 API 请求 Payload
     * @throws Error 如果验证失败，直接抛出错误信息
     */
    const buildValidatePayload = (): ValidateChatJoinReq => {
        try {
            return parseAndValidateJoinInfo(joinChatCredentialsForm.value)
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'UNKNOWN_VALIDATION_ERROR'

            // 映射错误代码到对应的 i18n 翻译
            let message: string
            let errorType = ErrorType.VALIDATION

            switch (errorMessage) {
                case 'ROOM_ID_REQUIRED':
                    message = 'Room ID is required'
                    break
                case 'INVALID_ROOM_ID_FORMAT':
                    message = 'Invalid Room ID format'
                    break
                case 'PASSWORD_REQUIRED':
                    message = 'Password is required'
                    break
                case 'INVITE_URL_REQUIRED':
                    message = t('validation.invite_url_required') || 'Invite URL is required'
                    break
                case 'INVALID_INVITE_URL_FORMAT':
                    message = 'Invalid Invite URL format'
                    break
                default:
                    message = 'Validation failed'
                    errorType = ErrorType.UNKNOWN
            }

            throw createAppError(
                errorType,
                message,
                {
                    severity: ErrorSeverity.ERROR,
                    component: 'useJoinForm',
                    action: 'buildValidatePayload',
                    originalError: error instanceof Error ? error : undefined
                }
            )
        }
    }

    /**
     * 第一步：验证房间信息
     * 1. 校验表单
     * 2. 调用 store 验证 API
     * 3. 成功则跳转到 /Join/:id (或者仅验证)
     * @param shouldRedirect 是否在验证成功后自动跳转 (默认 true)
     */
    const handleValidate = async (shouldRedirect = true): Promise<boolean> => {
        return appStore.runWithLoading(
            t('common.processing') || 'Processing...',
            async () => {
                isValidating.value = true
                userStore.clearValidatedJoinChatInfo() // 先清除旧数据

                try {
                    // 获取校验后的请求参数
                    const req = buildValidatePayload()

                    // 调用 store 验证 API
                    const roomData = await roomStore.validateRoomAccess(req)

                    // 如果不需要跳转（仅此时验证通过），直接返回 true
                    if (!shouldRedirect) return true

                    if (router.currentRoute.value.path === '/chat') return true

                    // 如果当前路径是首页，则跳转到加入页面
                    if (router.currentRoute.value.path === '/') {
                        router.push(`/Join/${roomData.chatCode}`)
                        return true
                    }

                    // 路由错误，抛出异常终止加入流程
                    throw createAppError(
                        ErrorType.ROUTER,
                        t('chat.router_error') || 'Router error',
                        {
                            severity: ErrorSeverity.WARNING,
                            component: 'useJoinForm',
                            action: 'handleValidate'
                        }
                    )
                } catch (e) {
                    // 如果是 AppError，则直接抛出
                    if (isAppError(e)) {
                        throw e
                    }

                    // 未知请求错误，抛出异常终止加入流程
                    throw createAppError(
                        ErrorType.NETWORK,
                        t('error.unknown_error'),
                        {
                            severity: ErrorSeverity.ERROR,
                            component: 'useJoinForm',
                            action: 'handleValidate',
                            originalError: e
                        }
                    )
                } finally {
                    isValidating.value = false
                }
            }
        )
    }

    // #endregion

    return {
        joinChatCredentialsForm,
        joinChatCredentialsFormRef,
        isValidating,
        isRoomIdPasswordMode,
        isInviteUrlMode,
        joinMode,
        joinChatCredentialsFormRules,
        resetJoinForm,
        toggleJoinMode,
        changeJoinMode,
        handleValidate,
        buildValidatePayload
    }
}
