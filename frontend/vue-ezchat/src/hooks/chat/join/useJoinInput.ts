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
export const useJoinInput = () => {
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
     * 验证表单并获取有效载荷
     * 1. 本地校验 (Validators)
     * 2. 远程校验 (API)
     * 3. 返回有效载荷或 null
     */
    const validateAndGetPayload = async (useGlobalLoading = true): Promise<ValidateChatJoinReq | null> => {
        const action = async () => {
            isValidating.value = true
            userStore.clearValidatedJoinChatInfo() // 先清除旧数据

            try {
                // 1. 本地解析与校验
                const req = parseAndValidateJoinInfo(joinChatCredentialsForm.value)

                // 2. 调用 store 验证 API (这一步会把结果存入 userStore.validatedChatRoom)
                await roomStore.validateRoomAccess(req)

                return req
            } catch (e) {
                const errorMessage = e instanceof Error ? e.message : 'UNKNOWN_VALIDATION_ERROR'

                // 如果是已知业务错误（Validator 抛出的），可以在这里转译或直接抛出
                // 但为了简化，这里统一让外部处理错误，或者在这里 catch 并返回 null

                // 映射错误代码到对应的 i18n 翻译 (复用原有逻辑)
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
                        // 如果是 API 错误 (AppError)，直接抛出让上层处理
                        if (isAppError(e)) throw e

                        message = 'Validation failed'
                        errorType = ErrorType.UNKNOWN
                }

                throw createAppError(
                    errorType,
                    message,
                    {
                        severity: ErrorSeverity.WARNING, // 校验失败通常只是警告
                        component: 'useJoinInput',
                        action: 'validateAndGetPayload',
                        originalError: e instanceof Error ? e : undefined
                    }
                )
            } finally {
                isValidating.value = false
            }
        }

        if (useGlobalLoading) {
            return appStore.runWithLoading(
                t('common.processing') || 'Processing...',
                action
            )
        } else {
            return action()
        }
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
        validateAndGetPayload
    }
}
