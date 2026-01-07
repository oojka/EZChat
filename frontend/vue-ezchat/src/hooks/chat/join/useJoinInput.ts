import { ref, computed } from 'vue'
import i18n from '@/i18n'
import { REGEX_CHAT_CODE, REGEX_INVITE_URL, getPasswordReg, parseAndValidateJoinInfo } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import type { JoinChatCredentialsForm, ValidateChatJoinReq } from '@/type'
import { ElMessage } from 'element-plus'

export const useJoinInput = () => {
    // const router = useRouter() // Unused and causes error outside setup
    const t = i18n.global.t

    const userStore = useUserStore()
    const appStore = useAppStore()
    const roomStore = useRoomStore()

    // #region State - 响应式状态定义

    /**
     * 主流程表单状态 - 核心数据容器
     * 
     * ## 字段说明
     * - `joinMode`: 加入模式，可选 'roomId/password'（房间ID+密码）或 'inviteUrl'（邀请链接）
     * - `chatCode`: 房间代码，8位数字，仅 roomId/password 模式使用
     * - `password`: 房间密码，仅 roomId/password 模式使用
     * - `inviteUrl`: 邀请链接URL，仅 inviteUrl 模式使用
     * - `inviteCode`: 从邀请链接解析出的邀请码，内部使用
     * 
     * ## 使用场景
     * 1. 首页加入区域：对邀请信息进行验证
     * 2. 路由跳转：携带参数到加入页面
     * 3. 对话框：收集用户输入的凭证
     */
    const joinChatCredentialsForm = ref<JoinChatCredentialsForm>({
        joinMode: 'roomId/password', // 默认使用房间ID+密码模式
        chatCode: '',
        password: '',
        inviteUrl: '',
        inviteCode: '',
    })

    /** 
     * Element Plus 表单引用
     * 用于调用表单验证方法（validate()）和重置方法（resetFields()）
     * 类型为 ElForm 的实例引用
     */
    const joinChatCredentialsFormRef = ref()

    /** 
     * 验证中状态标志
     * 用于控制UI加载状态，防止重复提交
     * true: 正在执行验证（本地或远程）
     * false: 验证完成或未开始
     */
    const isValidating = ref(false)

    // #endregion

    // #region Computed - 计算属性

    /**
     * 是否为房间ID+密码模式
     * 根据当前 joinMode 判断，用于条件渲染和验证规则切换
     */
    const isRoomIdPasswordMode = computed(() => {
        return joinChatCredentialsForm.value.joinMode === 'roomId/password'
    })

    /**
     * 是否为邀请链接模式
     * 根据当前 joinMode 判断，用于条件渲染和验证规则切换
     */
    const isInviteUrlMode = computed(() => {
        return joinChatCredentialsForm.value.joinMode === 'inviteUrl'
    })

    /**
     * 加入模式的双向绑定包装器
     * 
     * ## 设计目的
     * 1. 支持 v-model 绑定，简化组件使用
     * 2. 在模式切换时自动重置表单，防止数据混淆
     * 3. 提供统一的模式管理接口
     * 
     * ## 副作用处理
     * 当设置新模式时，会自动调用 resetJoinForm() 清空表单字段
     * 这是为了防止不同模式的数据相互干扰（如密码模式的数据残留在邀请链接模式中）
     */
    const joinMode = computed({
        get: () => joinChatCredentialsForm.value.joinMode,
        set: (val: 'roomId/password' | 'inviteUrl') => {
            joinChatCredentialsForm.value.joinMode = val
            // 切换模式时重置表单是个好习惯，防止数据混淆
            resetJoinForm()
        }
    })

    /**
     * Element Plus 表单验证规则
     * 
     * ## 规则设计原则
     * 1. 根据当前模式动态生成验证规则
     * 2. 支持国际化消息
     * 3. 触发时机：blur（失去焦点时验证）
     * 
     * ## 验证规则说明
     * - 房间ID+密码模式：
     *   - chatCode: 必填 + 8位数字格式验证
     *   - password: 必填 + 密码格式验证（8-20位，基础安全级别）
     * - 邀请链接模式：
     *   - inviteUrl: 必填 + URL格式验证
     * 
     * ## 国际化支持
     * 所有验证消息都通过 t() 函数获取，支持多语言切换
     */
    const joinChatCredentialsFormRules = computed(() => {
        const rules: any = {}

        if (isRoomIdPasswordMode.value) {
            // ID + 密码模式验证规则
            rules.chatCode = [
                // 房间代码必填验证
                { required: true, message: t('validation.chat_code_required'), trigger: 'blur' },
                // 房间代码格式验证（8位数字）
                { pattern: REGEX_CHAT_CODE, message: t('validation.chat_code_format'), trigger: 'blur' }
            ]
            rules.password = [
                // 密码必填验证
                { required: true, message: t('validation.password_required'), trigger: 'blur' },
                // 密码格式验证（8-20位，基础安全级别）
                {
                    pattern: getPasswordReg({ min: 8, max: 20, level: 'basic' }),
                    message: t('validation.password_format'),
                    trigger: 'blur'
                }
            ]
        } else {
            // 邀请链接模式验证规则
            rules.inviteUrl = [
                // 邀请链接必填验证
                { required: true, message: t('validation.invite_url_required'), trigger: 'blur' },
                // 邀请链接格式验证
                { pattern: REGEX_INVITE_URL, message: t('validation.invite_url_format'), trigger: 'blur' }
            ]
        }

        return rules
    })

    // #endregion

    // #region Methods - 业务方法

    /**
     * 重置主表单数据
     * 
     * ## 使用场景
     * 1. 对话框关闭时清理数据
     * 2. 加入模式切换时防止数据混淆
     * 3. 加入成功后重置表单状态
     * 
     * ## 重置策略
     * - 保留当前 joinMode（默认回退到房间ID+密码模式）
     * - 清空所有输入字段
     * - 保持表单引用不变
     */
    const resetJoinForm = () => {
        joinChatCredentialsForm.value = {
            joinMode: 'roomId/password', // 默认回退到密码模式
            chatCode: '',
            password: '',
            inviteUrl: '',
            inviteCode: '',
        }
    }

    /**
     * 手动切换加入模式（在两个模式间切换）
     * 
     * ## 切换逻辑
     * 1. 确定新模式（当前是房间ID模式则切换到邀请链接模式，反之亦然）
     * 2. 清空所有表单字段，防止数据混淆
     * 3. 更新 joinMode 状态
     * 
     * ## 与 changeJoinMode 的区别
     * - toggleJoinMode: 在两个模式间自动切换
     * - changeJoinMode: 直接设置指定模式
     */
    const toggleJoinMode = () => {
        const newMode = joinChatCredentialsForm.value.joinMode === 'roomId/password' ? 'inviteUrl' : 'roomId/password'
        // 先重置表单字段，防止不同模式的数据残留
        joinChatCredentialsForm.value.chatCode = ''
        joinChatCredentialsForm.value.password = ''
        joinChatCredentialsForm.value.inviteUrl = ''
        joinChatCredentialsForm.value.inviteCode = ''
        // 最后设置模式，确保模式正确应用
        joinChatCredentialsForm.value.joinMode = newMode
    }

    /**
     * 设置加入模式（接受参数指定模式）
     * 
     * ## 使用场景
     * 1. 组件初始化时设置默认模式
     * 2. 用户点击模式切换按钮时调用
     * 3. 根据业务逻辑动态切换模式
     * 
     * ## 参数说明
     * @param mode - 要设置的加入模式，可选 'roomId/password' 或 'inviteUrl'
     * 
     * ## 实现细节
     * 先清空表单字段，再设置模式，确保数据一致性
     */
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
     * 验证表单并获取有效载荷 - 核心验证方法
     * 
     * ## 验证流程（三步验证法）
     * 1. **本地校验**：使用 Validators 进行格式验证
     * 2. **远程校验**：调用 API 验证房间可访问性
     * 3. **结果处理**：返回有效载荷或抛出错误
     * 
     * ## 参数说明
     * @param useGlobalLoading - 是否使用全局加载状态，默认 
     * @returns 验证成功的有效载荷（ValidateChatJoinReq）或 null（验证失败）
     * 
     * ## 错误处理策略
     * 1. 本地验证错误：转换为 AppError 并抛出
     * 2. API 验证错误：直接抛出（由上层处理）
     * 3. 未知错误：包装为 UNKNOWN 类型错误
     * 
     * ## 状态管理
     * - 验证期间设置 isValidating = true
     * - 验证前清除旧的验证信息
     * - 验证成功后将结果存入 userStore.validatedChatRoom
     */
    const validateAndGetPayload =
        async (joinCredentialsReq: JoinChatCredentialsForm = joinChatCredentialsForm.value, useGlobalLoading: boolean = true): Promise<ValidateChatJoinReq | null> => {
            let useLoading = false
            const action = async () => {
                isValidating.value = true
                userStore.clearValidatedJoinChatInfo() // 先清除旧数据，防止数据污染

                try {
                    // [DEBUG] Log current form state to diagnose validation errors
                    console.log('[DEBUG] Validating Join Request:', JSON.parse(JSON.stringify(joinCredentialsReq)))

                    // 1. 本地解析与校验 - 使用工具函数验证格式
                    const req = parseAndValidateJoinInfo(joinCredentialsReq)

                    // 2. 调用 store 验证 API - 远程验证房间可访问性
                    // 这一步会把验证结果存入 userStore.validatedChatRoom，供后续步骤使用
                    await roomStore.validateRoomAccess(req)
                    useLoading = useGlobalLoading
                    return req
                } catch (e: any) {
                    // 处理已知的前端校验错误
                    const validationErrors: Record<string, string> = {
                        'ROOM_ID_REQUIRED': 'validation.chat_code_required',
                        'INVALID_ROOM_ID_FORMAT': 'validation.chat_code_format',
                        'PASSWORD_REQUIRED': 'validation.password_required',
                        'INVITE_URL_REQUIRED': 'validation.invite_url_required',
                        'INVALID_INVITE_URL_FORMAT': 'validation.invite_url_format'
                    }

                    if (e instanceof Error) {
                        const errorKey = validationErrors[e.message]
                        if (errorKey) {
                            ElMessage.warning(t(errorKey))
                            return null
                        }
                    }

                    if (isAppError(e)) {
                        throw e
                    } else {
                        throw createAppError(
                            ErrorType.UNKNOWN,
                            'api unknown error',
                            {
                                severity: ErrorSeverity.ERROR,
                                component: 'validateAndGetPayload',
                                action: 'catch',
                                originalError: e
                            }
                        )
                    }
                } finally {
                    isValidating.value = false // 无论成功失败，都要重置验证状态
                }
            }

            // 根据参数决定是否使用全局加载状态
            if (useLoading) {
                return appStore.runWithLoading(
                    t('common.processing'),
                    action
                )
            } else {
                return action()
            }
        }

    // #endregion

    // #region 导出接口
    return {
        // 状态
        joinChatCredentialsForm,      // 表单数据（响应式）
        joinChatCredentialsFormRef,   // 表单引用（Element Plus 表单实例）
        isValidating,                 // 验证中状态标志

        // 计算属性
        isRoomIdPasswordMode,         // 是否为房间ID+密码模式
        isInviteUrlMode,              // 是否为邀请链接模式
        joinMode,                     // 加入模式（支持双向绑定）
        joinChatCredentialsFormRules, // Element Plus 表单验证规则

        // 方法
        resetJoinForm,                // 重置表单数据
        toggleJoinMode,               // 切换加入模式（自动切换）
        changeJoinMode,               // 设置加入模式（指定模式）
        validateAndGetPayload         // 验证表单并获取有效载荷（核心方法）
    }
    // #endregion
}
