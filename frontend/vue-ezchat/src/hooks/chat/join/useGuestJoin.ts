import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { isValidNickname, isValidChatCode, isInviteJoinReq, isPasswordJoinReq } from '@/utils/validators.ts'
import { isAppError, createAppError, ErrorType, ErrorSeverity } from '@/error/ErrorTypes.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useImageStore } from '@/stores/imageStore.ts'
import { useRoomStore } from '@/stores/roomStore.ts'
import type { Image, GuestJoinReq } from '@/type'
import { ElMessage } from 'element-plus' // Added for feedback

/**
 * useGuestJoin
 * 职责：管理访客加入流程 (昵称、头像 -> 提交)
 */
export const useGuestJoin = () => {
    const router = useRouter()
    const route = useRoute()
    const { t } = useI18n()

    const userStore = useUserStore()
    const imageStore = useImageStore()
    const roomStore = useRoomStore() // Used for processJoinNavigation if needed, mainly router here

    // #region State

    /** * 访客信息状态 */
    const guestNickname = ref('')
    const guestAvatar = ref<Image>({
        imageThumbUrl: '',
        imageUrl: '',
        imageName: '',
        blobUrl: '',
        blobThumbUrl: '',
    })

    /** 默认头像 URL */
    const defaultAvatarUrl = ref('')

    /** UI 交互状态 */
    const isLoading = ref(false)

    // #endregion

    // #region Methods

    /** 初始化默认头像 */
    const initDefaultAvatarUrl = () => {
        defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('user')
    }

    /** 头像上传成功回调 */
    const handleAvatarSuccess = (response: any) => {
        if (response?.data) {
            guestAvatar.value.imageThumbUrl = response.data.imageThumbUrl || response.data.url || ''
            guestAvatar.value.imageUrl = response.data.imageUrl || response.data.url || ''
        }
    }

    /**
     * 访客加入核心逻辑
     */
    const handleGuestJoin = async (shouldNavigate = true): Promise<boolean> => {
        isLoading.value = true
        try {
            // 0. 类型安全获取路由参数 (当前 URL 上的 chatCode)
            const currentChatCode = Array.isArray(route.params.chatCode)
                ? route.params.chatCode[0]
                : route.params.chatCode;

            // 1. 验证房间号是否存在
            if (!currentChatCode) {
                throw createAppError(ErrorType.VALIDATION, 'Room ID is missing in URL', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useGuestJoin',
                    action: 'handleGuestJoin'
                })
            }

            if (!isValidChatCode(currentChatCode)) {
                throw createAppError(ErrorType.VALIDATION, 'Invalid Room ID format in URL', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useGuestJoin',
                    action: 'handleGuestJoin'
                })
            }

            // 验证一致性：确保 URL 的房间号与之前 validate 接口返回的一致 (userStore)
            if (currentChatCode !== userStore.validatedChatRoom?.chatCode) {
                throw createAppError(ErrorType.VALIDATION, 'Room code mismatch', {
                    severity: ErrorSeverity.WARNING,
                    component: 'useGuestJoin',
                    action: 'handleGuestJoin'
                })
            }

            // 2. 验证昵称
            if (!isValidNickname(guestNickname.value)) {
                ElMessage.warning(t('validation.nickname_invalid') || 'Invalid Nickname')
                return false
            }

            // 3. 头像处理：如果是默认头像且未上传，先上传
            if (!guestAvatar.value.imageUrl && !guestAvatar.value.imageThumbUrl) {
                try {
                    guestAvatar.value = await imageStore.uploadDefaultAvatarIfNeeded(guestAvatar.value, 'user')
                } catch (e) {
                    if (isAppError(e)) throw e
                    throw createAppError(ErrorType.NETWORK, 'Avatar upload failed', {
                        severity: ErrorSeverity.WARNING,
                        component: 'useGuestJoin',
                        action: 'handleGuestJoin',
                        originalError: e as Error,
                    })
                }
            }

            // 4. 获取并验证凭证
            const validatedReq = userStore.validateChatJoinReq
            if (!validatedReq) {
                throw createAppError(ErrorType.VALIDATION, 'Validation info expired, please restart', {
                    severity: ErrorSeverity.ERROR,
                    component: 'useGuestJoin',
                    action: 'handleGuestJoin'
                })
            }

            const targetCode = 'chatCode' in validatedReq ? validatedReq.chatCode : currentChatCode
            if (!targetCode) {
                throw createAppError(ErrorType.VALIDATION, 'Target chat code is missing', { severity: ErrorSeverity.ERROR, component: 'useGuestJoin', action: 'handleGuestJoin' })
            }

            // 5. 构建请求
            let req: GuestJoinReq | null = null;
            if (isPasswordJoinReq(validatedReq)) {
                req = {
                    nickName: guestNickname.value,
                    avatar: guestAvatar.value,
                    chatCode: validatedReq.chatCode,
                    password: validatedReq.password,
                }
            } else if (isInviteJoinReq(validatedReq)) {
                req = {
                    nickName: guestNickname.value,
                    avatar: guestAvatar.value,
                    inviteCode: validatedReq.inviteCode,
                }
            }

            if (!req) {
                throw createAppError(ErrorType.VALIDATION, 'Invalid validation info', { severity: ErrorSeverity.ERROR, component: 'useGuestJoin', action: 'handleGuestJoin' })
            }

            // 6. 调用 store 执行访客加入
            const success = await userStore.executeGuestJoin(req, currentChatCode)

            if (success) {
                ElMessage.success(t('chat.join_success') || 'Joined successfully')
                // 7. 导航
                // 7. 导航
                if (shouldNavigate) {
                    await roomStore.processJoinNavigation(targetCode)
                }
                return true
            }

            return false

        } catch (e) {
            const msg = isAppError(e) ? e.message : (e instanceof Error ? e.message : t('common.error'))
            ElMessage.error(msg)
            return false
        } finally {
            isLoading.value = false
        }
    }

    // #endregion

    return {
        guestNickname,
        guestAvatar,
        defaultAvatarUrl,
        isLoading,
        initDefaultAvatarUrl,
        handleAvatarSuccess,
        handleGuestJoin
    }
}
