import axios from "axios";
import {ElMessage} from "element-plus";
import router from "@/router";
import {type LoginUser} from "@/type";
import i18n from "@/i18n";

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
    if (!response.data.status) {
      let res: { status: 'error' | 'warning'; msg: string } = { status: 'error', msg: '' };
      const msg = response.data.message || '';

      if (!msg) res.msg = t('api.unexpected_error');
      else if (msg.includes('is already exist')) {
        res.status = 'warning';
        const match = msg.match(/'(.+)'/);
        const info = match ? match[1] : null;
        res.msg = info ? t('api.already_exists', { info }) : t('api.data_already_exists');
      } else if (msg.includes('User is not a member')) {
        res.msg = t('api.not_member');
        router.replace('/chat').catch(() => {});
      } else if (msg.includes('Chat room not found')) {
        res.msg = t('api.room_not_found');
        router.replace('/chat').catch(() => {});
      } else if (msg.includes('Input value too long')) {
        res.status = 'warning';
        res.msg = t('api.input_too_long');
      } else if (msg.includes('Data integrity violation')) {
        res.msg = t('api.data_integrity_error');
      } else if (msg.includes('Failed to join chat room as guest')) {
        res.msg = t('api.guest_join_failed');
      } else if (msg.includes('Incorrect timestamp format')) {
        res.msg = t('api.invalid_request');
        router.replace('/').catch(() => {});
      } else if (msg.includes('File is empty')) {
        res.status = 'warning';
        res.msg = t('api.file_empty');
      } else if (msg.includes('File upload failed')) {
        res.msg = t('api.file_upload_failed');
      } else if (msg.includes('File size exceeds limit')) {
        res.status = 'warning';
        res.msg = t('api.file_too_large');
      } else if (msg.includes('Invalid username or password')) {
        res.msg = t('api.invalid_credentials');
      } else {
        res.msg = msg;
      }

      if (res.status === 'warning') ElMessage.warning(res.msg);
      else ElMessage.error(res.msg);

      return Promise.reject(new Error(res.msg));
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
