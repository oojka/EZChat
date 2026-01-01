import axios from "axios";
import {ElMessage} from "element-plus";
import router from "@/router";
import {type LoginUser} from "@/type";
import i18n from "@/i18n";
import { useAppStore } from "@/stores/appStore";
import { useRoomStore } from "@/stores/roomStore";

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

const MAX_REQUESTS_PER_WINDOW = 10;
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
        }).catch(() => {});

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

      // 3. 特殊逻辑处理：路由跳转
      if (message) {
        // 业务原因：如果用户不在房间/房间不存在，房间列表可能已过期，需要刷新
        if (message.includes('User is not a member') || message.includes('Chat room not found')) {
          const roomStore = useRoomStore();
          roomStore.initRoomList();
        } else if (message.includes('Incorrect timestamp format')) {
          // 时间戳格式错误：通常是历史分页参数异常，回到首页重置
          router.replace('/').catch(() => {});
        }
      }

      // 针对 code 的特殊跳转逻辑
      if (code === 42001 || code === 42002) {
        // 房间不存在/非成员：回到聊天欢迎页，避免停留在非法房间
        router.replace('/chat').catch(() => {});
      } else if (code === 40100) {
        // 后端业务码表示未授权：清理登录态并回到首页
        localStorage.removeItem('loginUser');
        router.replace('/').catch(() => {});
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
    if (status === 401) {
      // HTTP 401：通常是 token 失效/缺失（由后端拦截器直接返回）
      ElMessage.closeAll();
      ElMessage.error(t('auth.session_expired'));
      localStorage.removeItem('loginUser');
      router.replace('/').catch(() => {});
    } else if (status >= 500) {
      // 500 错误也改用 state
      router.replace({ path: '/error', state: { code: status.toString() } }).catch(() => {});
    } else if (status === 403) {
      ElMessage.error(t('api.no_permission'));
      router.replace('/').catch(() => {});
    } else if (status === 429) {
      // HTTP 429：服务端限流，前端也进入短暂封锁期，减少重复请求
      ElMessage.error(t('api.too_many_requests'));
      localStorage.setItem('api_restriction_until', (Date.now() + 30000).toString());
      router.replace({ path: '/error', state: { code: '429' } }).catch(() => {});
    } else {
      ElMessage.error(t('api.unexpected_error'));
    }
    return Promise.reject(error);
  },
);

export default request;
