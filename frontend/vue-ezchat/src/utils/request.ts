import axios from "axios";
import {ElMessage} from "element-plus";
import router from "@/router";
import {type LoginUser} from "@/type";
import i18n from "@/i18n";
import { useAppStore } from "@/stores/appStore";
import { useRoomStore } from "@/stores/roomStore";

const { t } = i18n.global;

const request = axios.create({
  baseURL: "/api",
  timeout: 600000,
});

const MAX_REQUESTS_PER_WINDOW = 10;
const WINDOW_SIZE = 3 * 1000;
const LOCKOUT_DURATION = 10 * 1000;

request.interceptors.request.use(
  (config) => {
    const now = Date.now();
    const restrictionUntil = localStorage.getItem('api_restriction_until');

    if (restrictionUntil) {
      const until = parseInt(restrictionUntil);
      if (!isNaN(until) && until > now) {
        ElMessage.closeAll();
        localStorage.removeItem('loginUser');

        // 专业做法：使用 state 传递错误码，URL 保持干净
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

    if (now - windowStart > WINDOW_SIZE) {
      windowStart = now;
      requestCount = 1;
      localStorage.setItem('api_window_start', windowStart.toString());
      localStorage.setItem('api_req_count', requestCount.toString());
    } else {
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
        if (message.includes('User is not a member') || message.includes('Chat room not found')) {
          const roomStore = useRoomStore();
          roomStore.initRoomList();
        } else if (message.includes('Incorrect timestamp format')) {
          router.replace('/').catch(() => {});
        }
      }

      // 针对 code 的特殊跳转逻辑
      if (code === 42001 || code === 42002) {
        router.replace('/chat').catch(() => {});
      } else if (code === 40100) {
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
