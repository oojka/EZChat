import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";
import i18n from "@/i18n";
import { ref, watch, computed } from "vue";
import { useRoomStore } from "@/stores/roomStore";
import { useUserStore } from "@/stores/userStore";
import { useConfigStore } from "@/stores/configStore";
import { showAlertDialog } from "@/components/dialogs/AlertDialog";
import { useWebsocketStore } from "@/stores/websocketStore";
import { isAppError, createAppError, ErrorType, ErrorSeverity } from "@/error/ErrorTypes";
import { ErrorCode } from "@/error/ErrorCode";

const { t } = i18n.global;

/**
 * Axios 实例（全局请求入口）
 *
 * 约定：
 * - baseURL 使用 `/api`：由 Vite dev proxy 转发到后端真实路径（后端本身不带 `/api` 前缀）
 * - Token 通过请求头 `token` 传递（不是 Authorization Bearer）
 *
 * 拦截器职责：
 * - 请求拦截：前端侧“轻量限流” + 注入 token
 * - 响应拦截：统一处理 Result 结构（status/code/message），并做必要的路由跳转
 */
const request = axios.create({
  baseURL: "/api",
  timeout: 600000,
});

/**
 * 判断当前请求是否需要限流
 * 
 * @param url 请求 URL
 * @param rateLimitedApis 需要限流的 API 路径列表
 * @returns 是否需要限流
 */
const shouldRateLimit = (url: string, rateLimitedApis: string[]): boolean => {
  return rateLimitedApis.some(apiPath => url.includes(apiPath))
}

/**
 * 判断当前请求是否需要 Token
 * 
 * @param url 请求 URL
 * @param noTokenApis 不需要 Token 的 API 路径列表
 * @returns 是否需要 Token
 */
const needsToken = (url: string, noTokenApis: string[]): boolean => {
  return !noTokenApis.some(apiPath => url.includes(apiPath))
}

request.interceptors.request.use(
  /**
   * 请求拦截器
   *
   * 业务目的：
   * - 对"短时间内的高频请求”做前端侧限制，避免误触/刷接口导致后端压力
   * - 只对特定 API 进行限流拦截
   * - 在每次请求前自动注入 token（保持调用侧简单）
   */
  (config) => {
    const requestUrl = config.url || ''
    const configStore = useConfigStore()
    const userStore = useUserStore()
    const websocketStore = useWebsocketStore()
    const isRateLimited = shouldRateLimit(requestUrl, configStore.RATE_LIMITED_APIS)

    // 只对需要限流的 API 进行限流检查
    if (isRateLimited) {
      const now = Date.now();
      const restrictionUntil = localStorage.getItem('api_restriction_until');

      if (restrictionUntil) {
        const until = parseInt(restrictionUntil);
        if (!isNaN(until) && until > now) {
          ElMessage.closeAll();
          localStorage.removeItem('loginUser');

          // 处于封锁期：直接跳错误页（使用 state 传递错误码，避免 URL 暴露敏感参数）
          router.replace({
            path: '/error',
            state: { code: '429' }
          }).catch(() => { });

          return Promise.reject(new Error('REDIRECT_TO_ERROR_PAGE'));
        } else {
          localStorage.removeItem('api_restriction_until');
        }
      }

      let windowStart = parseInt(localStorage.getItem('api_window_start') || '0');
      let requestCount = parseInt(localStorage.getItem('api_req_count') || '0');

      // 1) 滑动窗口计数：超过窗口期则重置计数
      if (now - windowStart > configStore.WINDOW_SIZE) {
        windowStart = now;
        requestCount = 1;
        localStorage.setItem('api_window_start', windowStart.toString());
        localStorage.setItem('api_req_count', requestCount.toString());
      } else {
        // 2) 窗口期内：超过上限则进入封锁期
        if (requestCount >= configStore.MAX_REQUESTS_PER_WINDOW) {
          const lockoutTime = now + configStore.LOCKOUT_DURATION;
          localStorage.setItem('api_restriction_until', lockoutTime.toString());
          ElMessage.error(t('api.too_many_requests'));
          showAlertDialog({
            title: 'api.too_many_requests',
            message: 'api.too_many_requests',
            confirmText: 'common.confirm',
            type: 'error',
            onConfirm: () => {
              return Promise.reject(new Error('429_SIMULATED'));
            }
          });
          return Promise.reject(new Error('429_SIMULATED'));
        } else {
          requestCount++;
          localStorage.setItem('api_req_count', requestCount.toString());
        }
      }
    }

    // 3) 注入 token：后端拦截器读取 header `token`（仅对需要 token 的 API）
    const needsTokenHeader = needsToken(requestUrl, configStore.NO_TOKEN_APIS)
    if (needsTokenHeader) {
      // 必须检查 localStorage 中是否存在登录信息，防止用户删除 localStorage 后仍能使用 token
      const hasLoginInStorage = localStorage.getItem('loginUser') !== null || localStorage.getItem('loginGuest') !== null

      if (!hasLoginInStorage) {
        websocketStore.close();
        showAlertDialog({
          message: t('auth.session_expired') || 'Session expired',
          type: 'warning',
          onConfirm: () => {
            router.replace('/').catch(() => { });
          }
        });
        // 使用 createAppError 创建标记错误，避免响应拦截器重复显示错误消息
        return Promise.reject(createAppError(
          ErrorType.ROUTER,
          'Session expired',
          {
            severity: ErrorSeverity.WARNING,
            component: 'requestInterceptor',
            action: 'checkToken'
          }
        ));
      }

      // localStorage 中存在登录信息，但内存中没有 token 时，尝试恢复
      if (!userStore.hasToken()) {
        userStore.restoreLoginUserFromStorage() || userStore.restoreLoginGuestFromStorage();
      }

      // 每次请求都获取最新的 token（响应式，自动获取最新值）
      const currentToken = userStore.getAccessToken();
      if (currentToken) {
        config.headers.token = currentToken;
      }
    }

    return config;
  },
  (error) => Promise.reject(error),
);

request.interceptors.response.use(
  /**
   * 响应拦截器（业务成功/失败分流）
   *
   * 后端约定返回：{ status: 1|0, code, message, data }
   * - status=1：业务成功
   * - status=0：业务失败（仍可能是 HTTP 200）
   */
  async (response) => {
    const { status, code, message } = response.data;
    const userStore = useUserStore();
    const roomStore = useRoomStore();
    // 如果 status 不为 1，表示业务失败
    if (!status) {
      let errorMsg = "";

      // 1. 优先尝试根据业务错误码进行多语言映射
      if (code) {
        const langKey = `api.errors.${code}`;
        const translated = t(langKey);
        // 如果翻译出的内容不是 key 本身，说明翻译成功
        if (translated !== langKey) {
          errorMsg = translated;
        }
      }

      // 2. 如果没有 code 或翻译失败，则使用后端返回的 message 字符串
      if (!errorMsg) {
        errorMsg = message || t('api.unexpected_error');
      }

      // 3. 基于错误消息的特殊业务逻辑处理（在路由跳转之前）
      if (message) {
        // 业务原因：如果用户不在房间/房间不存在，房间列表可能已过期，需要刷新
        if (userStore.hasToken() && (message.includes('User is not a member') || message.includes('Chat room not found'))) {
          // 如果用户有 token，则刷新房间列表
          roomStore.initRoomList();
        } else if (message.includes('Incorrect timestamp format')) {
          // 时间戳格式错误：通常是历史分页参数异常，回到首页重置
          router.replace('/').catch(() => { });
        }
      }

      // 4. 根据业务错误码进行路由跳转
      if (code) {
        switch (code) {
          case ErrorCode.CHAT_NOT_FOUND: // 42001
            // 如果当前路径在 /chat 下，回到聊天欢迎页
            showAlertDialog({
              message: t('api.room_not_found'),
              type: 'warning',
            });
            if (router.currentRoute.value.path.startsWith('/chat')) {
              router.replace('/chat').catch(() => { });
            }
            return false;

          case ErrorCode.NOT_A_MEMBER: // 42002
            // 非成员：如果当前路径在 /chat 下，回到聊天欢迎页
            if (router.currentRoute.value.path.startsWith('/chat')) {
              // 无访问权限：您不是该聊天室的成员
              ElMessage.error(t('api.not_member'));
              router.replace('/chat').catch(() => { });
            }
            break;

          case ErrorCode.UNAUTHORIZED: // 40100
            // 后端业务码表示未授权：清理登录态并回到首页
            localStorage.removeItem('loginUser');
            router.replace('/').catch(() => { });
            break;

          case ErrorCode.FORBIDDEN: // 40300
            // 禁止访问：根据错误消息区分不同场景
            const msg = (message || '').toLowerCase();
            // 该房间被禁止加入
            if (msg.includes('join is disabled')) {
              ElMessage.error(t('api.join_disabled'));
              break;

              // 该房间被禁止通过密码加入
            } else if (msg.includes('password login is not enabled')) {
              ElMessage.error(t('api.password_login_disabled'));
              break;
            } else {
              // 其他 40300 错误，默认使用 join_disabled
              ElMessage.error(t('api.join_disabled'));
            }
            break;

          case ErrorCode.PASSWORD_REQUIRED: // 42003
            // 该房间需要密码才能加入
            ElMessage.error(t('api.password_required'));
            break;

          case ErrorCode.PASSWORD_INCORRECT: // 42004
            // 密码验证失败：密码不正确
            ElMessage.error(t('api.password_incorrect'));
            break;

          case ErrorCode.BAD_REQUEST: // 40000
            // 请求参数错误：如果是加入验证相关错误，跳转到错误页
            const badRequestMsg = (message || '').toLowerCase();
            if (badRequestMsg.includes('validation') ||
              badRequestMsg.includes('join')) {
              router.push('/join/error?reason=validation_failed').catch(() => { });
            }
            return false;

          case ErrorCode.INVITE_CODE_INVALID: // 42010
            ElMessage.error(t('api.invite_code_invalid'));
            break;

          case ErrorCode.INVITE_CODE_EXPIRED: // 42011
            showAlertDialog({
              message: t('api.invite_code_expired'),
              type: 'warning',
            });
            return false;

          case ErrorCode.INVITE_CODE_REVOKED: // 42012
            showAlertDialog({
              message: t('api.invite_code_revoked'),
              type: 'warning',
            });
            return false;

          case ErrorCode.INVITE_CODE_USAGE_LIMIT_REACHED: // 42013
            showAlertDialog({
              message: t('api.invite_code_usage_limit_reached'),
              type: 'warning',
            });
            return false;

          case ErrorCode.DATABASE_ERROR: // 50001
            // 根据请求 URL 路径区分：如果是加入聊天室相关的请求，显示特殊提示
            const requestUrl = response.config?.url || ''
            if (requestUrl.includes('/chat/join') || requestUrl.includes('/auth/guest/join')) {
              return Promise.reject(new Error('IS_ALREADY_JOINED'));
            }
            // 数据库冲突：数据库中已存在相同的记录
            ElMessage.error(message.split("'")[1].trim() + t('api.is_already_exist'));
            return false;
          default:
            break;
        }
      }

      throw createAppError(

        ErrorType.UNKNOWN,
        errorMsg,
        {
          severity: ErrorSeverity.ERROR,
          component: 'responseInterceptor',
          action: 'handleResponse'
        }
      );
    }
    return response.data;
  },
  (error) => {
    // 如果是请求拦截器已经处理过的会话过期错误，直接跳过，避免重复显示
    if (isAppError(error) && error.type === ErrorType.ROUTER && error.message === 'Session expired') {
      return Promise.reject(error);
    }

    const { response } = error;
    if (!response) {
      ElMessage.error(t('api.network_error'));
      return Promise.reject(error);
    }

    const status = response.status;
    switch (status) {
      case 400:
        // HTTP 400：请求参数错误，不跳转（用户可修正）
        ElMessage.error(t('api.http_400'));
        break;

      case 401:
        // HTTP 401：通常是 token 失效/缺失（由后端拦截器直接返回）
        showAlertDialog({
          message: t('auth.session_expired') || 'Session expired',
          type: 'warning',
        });
        localStorage.removeItem('loginUser');
        router.replace('/').catch(() => { });
        return false;

      case 403:
        // HTTP 403：权限被拒绝
        ElMessage.error(t('api.no_permission') || 'No permission');
        router.replace('/').catch(() => { });
        break;

      case 404:
        // HTTP 404：资源未找到
        router.replace({ path: '/404', state: { code: '404' } }).catch(() => { });
        break;

      case 408:
        // HTTP 408：请求超时，不跳转（用户可重试）
        ElMessage.error(t('api.http_408') || 'Request timeout');
        break;

      case 429:
        // HTTP 429：服务端限流，前端也进入短暂封锁期，减少重复请求
        ElMessage.error(t('api.too_many_requests'));
        localStorage.setItem('api_restriction_until', (Date.now() + 30000).toString());
        router.replace({ path: '/error', state: { code: '429' } }).catch(() => { });
        break;

      case 502:
      case 503:
      case 504:
        // HTTP 502/503/504：网关错误/服务不可用/网关超时
        router.replace({ path: '/error', state: { code: status.toString() } }).catch(() => { });
        break;

      default:
        if (status >= 500) {
          // HTTP 500+：服务器错误
          router.replace({ path: '/error', state: { code: status.toString() } }).catch(() => { });
        } else {
          // 其他未处理的 HTTP 错误
          ElMessage.error(t('api.unexpected_error'));
        }
    }
    return Promise.reject(error);
  },
);

export default request;
