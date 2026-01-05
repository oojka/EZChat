import axios from "axios";
import { ElMessage } from "element-plus";
import router from "@/router";
import { type LoginUser } from "@/type";
import i18n from "@/i18n";
import { useRoomStore } from "@/stores/roomStore";
import { showAlertDialog } from "@/components/dialogs/AlertDialog";

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

const MAX_REQUESTS_PER_WINDOW = 15;
const WINDOW_SIZE = 3 * 1000;
const LOCKOUT_DURATION = 10 * 1000;

request.interceptors.request.use(
  /**
   * 请求拦截器
   *
   * 业务目的：
   * - 对“短时间内的高频请求”做前端侧限制，避免误触/刷接口导致后端压力
   * - 在每次请求前自动注入 token（保持调用侧简单）
   */
  (config) => {
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
    if (now - windowStart > WINDOW_SIZE) {
      windowStart = now;
      requestCount = 1;
      localStorage.setItem('api_window_start', windowStart.toString());
      localStorage.setItem('api_req_count', requestCount.toString());
    } else {
      // 2) 窗口期内：超过上限则进入封锁期
      if (requestCount >= MAX_REQUESTS_PER_WINDOW) {
        const lockoutTime = now + LOCKOUT_DURATION;
        localStorage.setItem('api_restriction_until', lockoutTime.toString());
        ElMessage.error(t('api.too_many_requests'));
        showAlertDialog({
          title: t('api.too_many_requests'),
          message: t('api.too_many_requests'),
          confirmText: t('common.confirm'),
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

    // 3) 注入 token：后端拦截器读取 header `token`
    const str = localStorage.getItem('loginUser');
    const loginUser: LoginUser | null = str ? JSON.parse(str) : null;
    if (loginUser?.token) {
      config.headers.token = loginUser.token;
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
  (response) => {
    const { status, code, message } = response.data;

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
        if (message.includes('User is not a member') || message.includes('Chat room not found')) {
          const roomStore = useRoomStore();
          roomStore.initRoomList();
        } else if (message.includes('Incorrect timestamp format')) {
          // 时间戳格式错误：通常是历史分页参数异常，回到首页重置
          router.replace('/').catch(() => { });
        }
      }

      // 4. 根据业务错误码进行路由跳转
      if (code) {
        switch (code) {
          case 42001: // CHAT_NOT_FOUND
            // 如果当前路径在 /chat 下，回到聊天欢迎页
            if (router.currentRoute.value.path.startsWith('/chat')) {
              router.replace('/chat').catch(() => { });
            }
            break;

          case 42002: // NOT_A_MEMBER
            // 非成员：如果当前路径在 /chat 下，回到聊天欢迎页
            if (router.currentRoute.value.path.startsWith('/chat')) {
              // 无访问权限：您不是该聊天室的成员
              ElMessage.error(t('api.not_member'));
              router.replace('/chat').catch(() => { });
            }
            break;

          case 40100: // UNAUTHORIZED
            // 后端业务码表示未授权：清理登录态并回到首页
            localStorage.removeItem('loginUser');
            router.replace('/').catch(() => { });
            break;

          case 40300: // FORBIDDEN
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

          case 42003: // PASSWORD_REQUIRED
            // 该房间需要密码才能加入
            ElMessage.error(t('api.password_required'));
            break;

          case 42004: // PASSWORD_INCORRECT
            // 密码验证失败：密码不正确
            ElMessage.error(t('api.password_incorrect'));
            break;

          case 40000: // BAD_REQUEST
            // 请求参数错误：如果是加入验证相关错误，跳转到错误页
            const badRequestMsg = (message || '').toLowerCase();
            if (badRequestMsg.includes('validation') || badRequestMsg.includes('验证') ||
              badRequestMsg.includes('join') || badRequestMsg.includes('加入')) {
              router.push('/join/error?reason=validation_failed').catch(() => { });
            }
            // 其他 BAD_REQUEST 不跳转（可能是参数错误，用户可修正）
            break;
        }
      }

      ElMessage.error(errorMsg);
      return Promise.reject(new Error(errorMsg));
    }
    return response.data;
  },
  (error) => {
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
        ElMessage.closeAll();
        ElMessage.error(t('auth.session_expired'));
        localStorage.removeItem('loginUser');
        router.replace('/').catch(() => { });
        break;

      case 403:
        // HTTP 403：权限被拒绝
        ElMessage.error(t('api.no_permission'));
        router.replace('/').catch(() => { });
        break;

      case 404:
        // HTTP 404：资源未找到
        router.replace({ path: '/404', state: { code: '404' } }).catch(() => { });
        break;

      case 408:
        // HTTP 408：请求超时，不跳转（用户可重试）
        ElMessage.error(t('api.http_408'));
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
