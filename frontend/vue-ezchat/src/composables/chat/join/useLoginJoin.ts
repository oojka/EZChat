import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { isInviteJoinReq, isPasswordJoinReq } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import type { LoginForm, JoinChatReq } from '@/type'
import { ElMessage } from 'element-plus'

/**
 * useLoginJoin - 登录用户加入流程管理 Hook
 * 
 * ## 核心职责
 * 1. 管理登录用户的加入流程（登录后加入）
 * 2. 处理两种场景：未登录用户先登录再加入、已登录用户直接加入
 * 3. 集成登录认证与房间加入的业务逻辑
 * 4. 处理应用初始化和状态同步
 * 
 * ## 使用场景
 * 1. **场景A**：加入页面，用户未登录 → 填写账号密码 → 登录 → 自动加入
 * 2. **场景B**：已登录用户在对话框或快速访问中直接加入房间
 * 
 * ## 架构设计
 * - **认证层**：处理用户登录认证
 * - **加入层**：处理房间加入逻辑
 * - **初始化层**：登录后应用状态初始化
 * - **导航层**：加入成功后的页面跳转
 * 
 * ## 重要特性
 * - 支持两种加入方式：房间ID+密码、邀请链接
 * - 自动处理登录后的应用初始化
 * - 完整的错误处理和用户反馈
 * - 状态一致性保证
 */
export const useLoginJoin = () => {
    useI18n()
    const userStore = useUserStore()
    const roomStore = useRoomStore()
    const appStore = useAppStore()

    /**
     * 加载状态标志
     * 控制UI交互状态，防止重复提交
     * true: 正在执行登录或加入操作
     * false: 操作完成或未开始
     */
    const isLoading = ref(false)

    /**
     * 场景A：登录并加入 - 六步流程
     * 
     * ## 使用场景
     * 在加入页面（/join/:chatCode），用户选择登录方式加入
     * 用户填写账号密码 → 登录认证 → 自动加入目标房间
     * 
     * ## 完整流程
     * 1. 验证房间信息 → 2. 执行登录 → 3. 构建加入请求 → 
     * 4. 加入房间 → 5. 初始化应用 → 6. 导航跳转
     * 
     * ## 参数说明
     * @param loginForm - 登录表单数据（用户名、密码）
     * @param shouldNavigate - 是否在成功后自动导航，默认 true
     * @returns 是否成功登录并加入（boolean）
     * 
     * ## 关键逻辑
     * - 登录成功后必须初始化应用（拉取用户信息、房间列表等）
     * - 支持两种加入方式：房间ID+密码、邀请链接
     * - 处理"已加入"状态，避免重复加入
     */
    const handleLoginAndJoin = async (loginForm: LoginForm, shouldNavigate = true): Promise<boolean> => {
        isLoading.value = true
        try {
            // 1. 检查是否存在之前验证过的房间信息
            // 这些信息在用户访问加入页面时已经通过验证接口获取
            if (!userStore.validatedChatRoom || !userStore.validateChatJoinReq) {
                throw createAppError(ErrorType.VALIDATION, 'Validation info lost, please retry', { severity: ErrorSeverity.ERROR, component: 'useLoginJoin', action: 'handleLoginAndJoin' })
            }

            // 2. 执行用户登录认证
            const loginUser = await userStore.loginRequest(loginForm.username, loginForm.password)
            if (!loginUser) return false // 登录失败，已显示错误消息

            // 3. 根据验证信息类型构建对应的加入请求
            const validatedReq = userStore.validateChatJoinReq
            let joinReq: JoinChatReq
            if (isInviteJoinReq(validatedReq)) {
                // 邀请链接模式：只需要邀请码
                joinReq = { inviteCode: validatedReq.inviteCode }
            } else if (isPasswordJoinReq(validatedReq)) {
                // 房间ID+密码模式：需要房间代码和密码
                joinReq = { chatCode: validatedReq.chatCode, password: validatedReq.password }
            } else {
                throw createAppError(ErrorType.VALIDATION, 'Invalid validation info type', { severity: ErrorSeverity.ERROR, component: 'useLoginJoin', action: 'handleLoginAndJoin' })
            }

            // 4. 调用 store 执行房间加入
            const result = await roomStore.joinChat(joinReq)

            // 5. 处理加入结果：成功或已加入都需要继续流程
            if (result === 'SUCCESS' || result === 'ALREADY_JOINED') {
                // 5.1 获取目标聊天室代码
                const targetCode = userStore.validatedChatRoom.chatCode

                // 5.2 初始化应用状态（登录后必须）
                // 登录成功后需要初始化应用：拉取用户信息、房间列表、权限等
                // 这是关键步骤，确保应用状态与服务器同步
                await appStore.initializeApp(userStore.getAccessToken(), 'login', { waitForRoute: '/chat' })

                // 6. 导航到目标聊天室
                if (shouldNavigate) {
                    await roomStore.processJoinNavigation(targetCode)
                }
                return true
            }

            return false

        } catch (e) {
            // 统一错误处理：登录失败或加入失败
            const msg = isAppError(e) ? e.message : (e instanceof Error ? e.message : 'Login/Join failed')
            ElMessage.error(msg)
            return false
        } finally {
            isLoading.value = false // 确保加载状态被重置
        }
    }


    /**
     * 场景B：已登录用户直接加入 - 三步流程
     * 
     * ## 使用场景
     * 1. 已登录用户在加入对话框中直接加入
     * 2. 快速访问链接直接加入
     * 3. 任何已认证状态下的房间加入操作
     * 
     * ## 完整流程
     * 1. 执行加入 → 2. 处理结果 → 3. 导航跳转
     * 
     * ## 参数说明
     * @param req - 加入请求（JoinChatReq），包含房间加入所需凭证
     * @param shouldNavigate - 是否在成功后自动导航，默认 true
     * @returns 加入结果：'SUCCESS' | 'ALREADY_JOINED' | 'FAILED'
     * 
     * ## 关键逻辑
     * - 支持两种结果：成功加入、已加入（避免重复）、失败
     * - 自动处理目标聊天室代码获取（从验证信息或请求中提取）
     * - 已加入状态也视为成功，但可能需要特殊处理（如显示提示）
     */
    const executeJoin = async (req: JoinChatReq, shouldNavigate = true): Promise<'SUCCESS' | 'ALREADY_JOINED' | 'FAILED'> => {
        isLoading.value = true
        try {
            // 1. 调用 store 执行房间加入
            const result = await roomStore.joinChat(req)

            // 2. 处理加入结果
            if (result === 'SUCCESS' || result === 'ALREADY_JOINED') {
                // 2.1 确定目标聊天室代码
                // 优先从验证信息中获取，其次从请求中提取
                // 注意：邀请码模式需要在验证阶段获取对应的chatCode
                const targetCode = userStore.validatedChatRoom?.chatCode || ('chatCode' in req ? req.chatCode : '')

                // 2.2 导航到目标聊天室
                if (shouldNavigate) {
                    await roomStore.processJoinNavigation(targetCode)
                }
                return result
            }

            return 'FAILED'

        } catch (e) {
            // 统一错误处理
            const msg = isAppError(e) ? e.message : (e instanceof Error ? e.message : 'Join failed')
            ElMessage.error(msg)
            return 'FAILED'
        } finally {
            isLoading.value = false // 确保加载状态被重置
        }
    }

    // #region 导出接口
    return {
        // 状态
        isLoading,            // 加载状态标志（响应式）
        
        // 方法
        handleLoginAndJoin,   // 场景A：登录并加入（六步流程）
        executeJoin           // 场景B：已登录用户直接加入（三步流程）
    }
    // #endregion
}
