import type { AxiosInstance, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'
import { createAppError, ErrorSeverity, ErrorType } from '@/error/ErrorTypes'
import { ErrorCode } from '@/error/ErrorCode'
import type { RetryableRequestConfig } from '@/type'
import { handleUnauthorized } from '../../refresh'

const { t } = i18n.global

export const createBusinessResponseHandler = (request: AxiosInstance) => {
  return async (response: AxiosResponse) => {
    const { status, code, message } = response.data
    const userStore = useUserStore()
    const roomStore = useRoomStore()

    // 如果 status 不为 1，表示业务失败
    if (!status) {
      let errorMsg = ''

      // 1. 优先尝试根据业务错误码进行多语言映射
      if (code) {
        const langKey = `api.errors.${code}`
        const translated = t(langKey)
        // 如果翻译出的内容不是 key 本身，说明翻译成功
        if (translated !== langKey) {
          errorMsg = translated
        }
      }

      // 2. 如果没有 code 或翻译失败，则使用后端返回的 message 字符串
      if (!errorMsg) {
        errorMsg = message || t('api.unexpected_error')
      }

      // 3. 基于错误消息的特殊业务逻辑处理（在路由跳转之前）
      if (message) {
        // 业务原因：如果用户不在房间/房间不存在，房间列表可能已过期，需要刷新
        if (userStore.hasToken() && (message.includes('User is not a member') || message.includes('Chat room not found'))) {
          // 如果用户有 token，则刷新房间列表
          roomStore.initRoomList()
        } else if (message.includes('Incorrect timestamp format')) {
          // 时间戳格式错误：通常是历史分页参数异常，回到首页重置
          router.replace('/').catch(() => { })
        }
      }

      // 4. 根据业务错误码进行路由跳转
      if (code) {
        const isInputJoin: boolean = router.currentRoute.value.name === 'ChatRoom' ||
          router.currentRoute.value.name === 'Welcome' ||
          router.currentRoute.value.name === 'Home'

        switch (code) {
          case ErrorCode.NOT_A_MEMBER: // 42002
            // 非成员：如果当前路径在 /chat 下，回到聊天欢迎页
            if (router.currentRoute.value.path.startsWith('/chat')) {
              // 无访问权限：您不是该聊天室的成员
              ElMessage.error(t('api.not_member'))
              router.replace('/chat').catch(() => { })
            }
            break

          case ErrorCode.UNAUTHORIZED: // 40100
          case ErrorCode.TOKEN_EXPIRED: // 40101
            {
              const config: RetryableRequestConfig = response.config
              return handleUnauthorized(request, config, response.data)
            }

          case ErrorCode.FORBIDDEN: // 40300
            // 禁止访问：根据错误消息区分不同场景
            const msg = (message || '').toLowerCase()
            // 该房间被禁止加入
            if (msg.includes('join is disabled')) {
              ElMessage.error(t('api.join_disabled'))
              break

              // 该房间被禁止通过密码加入
            } else if (msg.includes('password login is not enabled')) {
              ElMessage.error(t('api.password_login_disabled'))
              break
            } else {
              // 其他 40300 错误，默认使用 join_disabled
              ElMessage.error(t('api.join_disabled'))
            }
            break

          case ErrorCode.CHAT_NOT_FOUND: // 42001
            // 如果当前路径在 /chat 下，回到聊天欢迎页
            if (isInputJoin) {
              // 不显示全局 Alert，直接 reject，让 JoinChatDialog 捕获并显示 Error Page
              return Promise.reject(new Error('api.room_not_found'))
            }
            return false

          case ErrorCode.PASSWORD_REQUIRED: // 42003
            // 该房间需要密码才能加入
            ElMessage.error(t('api.password_required'))
            break

          case ErrorCode.PASSWORD_INCORRECT: // 42004
            // 密码验证失败：密码不正确
            ElMessage.error(t('api.password_incorrect'))
            break

          case ErrorCode.BAD_REQUEST: // 40000
            // 请求参数错误：如果是加入验证相关错误，跳转到错误页
            const badRequestMsg = (message || '').toLowerCase()
            if (badRequestMsg.includes('validation') ||
              badRequestMsg.includes('join')) {
              router.push('/join/error?reason=validation_failed').catch(() => { })
            }
            return false

          case ErrorCode.INVITE_CODE_INVALID: // 42010
            if (isInputJoin) {
              return Promise.reject(new Error('api.invite_code_invalid'))
            }
            return false

          case ErrorCode.INVITE_CODE_EXPIRED: // 42011
            if (isInputJoin) {
              return Promise.reject(new Error('api.invite_code_expired'))
            }
            return false

          case ErrorCode.INVITE_CODE_REVOKED: // 42012
            if (isInputJoin) {
              return Promise.reject(new Error('api.invite_code_revoked'))
            }
            return false

          case ErrorCode.INVITE_CODE_USAGE_LIMIT_REACHED: // 42013
            showAlertDialog({
              message: t('api.invite_code_usage_limit_reached'),
              type: 'warning',
            })
            return false

          case ErrorCode.DATABASE_ERROR: // 50001
            // 根据请求 URL 路径区分：如果是加入聊天室相关的请求，显示特殊提示
            const requestUrl = response.config?.url || ''
            if (requestUrl.includes('/chat/join') || requestUrl.includes('/auth/guest/join')) {
              return Promise.reject(new Error('IS_ALREADY_JOINED'))
            }
            // 数据库冲突：数据库中已存在相同的记录
            ElMessage.error(message.split("'")[1].trim() + t('api.is_already_exist'))
            return false
          default:
            break
        }
      }

      throw createAppError(
        ErrorType.UNKNOWN,
        errorMsg,
        {
          severity: ErrorSeverity.ERROR,
          component: 'responseInterceptor',
          action: 'handleResponse',
          originalError: response.data
        }
      )
    }
    return response.data
  }
}
