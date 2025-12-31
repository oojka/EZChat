<script setup lang="ts">
import {computed, type Component} from 'vue'
import {
  Back,
  CircleCloseFilled,
  HomeFilled,
  InfoFilled,
  Switch,
  Warning
} from '@element-plus/icons-vue'

interface Props {
  code?: string | number
  locale?: string
}

const props = withDefaults(defineProps<Props>(), {
  code: '404',
  locale: 'ja'
})

const emit = defineEmits<{
  (e: 'home'): void
  (e: 'back'): void
  (e: 'change-lang', lang: string): void
}>()

/**
 * 静态错误数据 Map
 */
const ERROR_DATA_MAP: Record<string, Record<string, { title: string; message: string }>> = {
  'zh': {
    '404': { title: '页面未找到', message: '抱歉，您访问的页面可能已被移动、删除或暂时不可用。' },
    '403': { title: '访问受限', message: '您没有权限访问此资源。请确保您已登录或拥有相应的操作权限。' },
    '429': { title: '请求过于频繁', message: '系统检测到异常的访问频率。为了保障服务安全，请稍等片刻后再试。' },
    '500': { title: '服务器错误', message: '抱歉，系统出现临时故障。我们的工程师正在努力修复，请稍后重试。' },
    '503': { title: '服务维护中', message: '服务器目前正处于维护状态或负载过高，请稍后再回来看看。' },
    'default': { title: '发生了未知错误', message: '抱歉，程序运行出现异常。请尝试刷新页面或联系技术支持。' },
    'btn_back': { title: '返回', message: '' },
    'btn_home': { title: '首页', message: '' }
  },
  'zh-tw': {
    '404': { title: '頁面未找到', message: '抱歉，您訪問的頁面可能已被移動、刪除或暫時不可用。' },
    '403': { title: '訪問受限', message: '您沒有權限訪問此資源。請確保您已登入或擁有相應的操作權限。' },
    '429': { title: '請求過於頻繁', message: '系統檢測到異常的訪問頻率。為了保障服務安全，請稍等片刻後再試。' },
    '500': { title: '伺服器錯誤', message: '抱歉，系統出現臨時故障。我們的工程師正在努力修復，請稍後重試。' },
    '503': { title: '服務維護中', message: '伺服器目前正處於維護狀態或負載過高，請稍後再回來看看。' },
    'default': { title: '發生了未知錯誤', message: '抱歉，程式運行出現異常。請嘗試重新整理頁面或聯繫技術支持。' },
    'btn_back': { title: '返回', message: '' },
    'btn_home': { title: '首頁', message: '' }
  },
  'ja': {
    '404': { title: 'ページが見つかりません', message: 'お探しのページは移動したか、削除された可能性があります。URLをご確認ください。' },
    '403': { title: 'アクセス権限がありません', message: 'このコンテンツを表示する権限がありません。ログイン状態をご確認ください。' },
    '429': { title: 'アクセス制限', message: '短時間での過度なリクエストが検出されました。安全のため、しばらく時間を置いてから再度お試しください。' },
    '500': { title: 'サーバーエラー', message: 'システムで一時的な問題が発生しています。現在復旧作業を行っておりますので、しばらくお待ちください。' },
    '503': { title: 'サービス利用不可', message: 'サーバーがメンテナンス中か、一時的に過負荷の状態です。後ほど再度アクセスしてください。' },
    'default': { title: 'エラーが発生しました', message: '予期せぬエラーが発生しました。ページを更新するか、管理者にお問い合わせください。' },
    'btn_back': { title: '戻る', message: '' },
    'btn_home': { title: 'ホーム', message: '' }
  },
  'en': {
    '404': { title: 'Page Not Found', message: 'The page you are looking for might have been removed or is temporarily unavailable.' },
    '403': { title: 'Access Denied', message: 'You do not have the necessary permissions to view this resource.' },
    '429': { title: 'Too Many Requests', message: "We've detected an unusual amount of activity. Please wait a moment and try again." },
    '500': { title: 'Internal Server Error', message: 'Something went wrong on our end. Our team is working to fix the issue.' },
    '503': { title: 'Service Unavailable', message: 'The server is temporarily unable to handle your request due to maintenance.' },
    'default': { title: 'An Error Occurred', message: 'An unexpected error occurred. Please refresh the page or contact support.' },
    'btn_back': { title: 'BACK', message: '' },
    'btn_home': { title: 'HOME', message: '' }
  },
  'ko': {
    '404': { title: '페이지를 찾을 수 없습니다', message: '요청하신 페이지가 삭제되었거나 주소가 변경되어 현재 사용할 수 없습니다.' },
    '403': { title: '접근 권한 없음', message: '이 페이지에 접근할 수 있는 권한이 없습니다. 로그인 상태를 확인해 주세요.' },
    '429': { title: '접근 제한', message: '단시간에 너무 많은 요청이 감지되었습니다. 서비스 안전을 위해 잠시 후 다시 시도해 주세요.' },
    '500': { title: '서버 오류', message: '서버에 일시적인 문제가 발생했습니다. 현재 복구 중이오니 잠시 후 다시 시도해 주세요.' },
    '503': { title: '서비스 점검 중', message: '서버 점검 중이거나 일시적인 과부하 상태입니다. 잠시 후 다시 방문해 주세요.' },
    'default': { title: '오류가 발생했습니다', message: '예기치 않은 오류가 발생했습니다. 페이지를 새로고침하거나 고객 지원에 문의해 주세요.' },
    'btn_back': { title: '이전', message: '' },
    'btn_home': { title: '홈', message: '' }
  }
}

const errorContent = computed(() => {
  const lang = props.locale || 'ja'
  const code = props.code.toString()
  const data = ERROR_DATA_MAP[lang] || ERROR_DATA_MAP['en']

  const configMap: Record<string, { type: 'info' | 'warning' | 'error'; icon: Component }> = {
    '404': { type: 'info', icon: CircleCloseFilled },
    '403': { type: 'warning', icon: InfoFilled },
    '429': { type: 'warning', icon: Warning },
    '500': { type: 'error', icon: Warning },
    '503': { type: 'error', icon: Warning }
  }

  const config = configMap[code] || { type: 'warning', icon: InfoFilled }
  const content = data[code] || data['default']

  return {
    ...config,
    ...content,
    btnBack: data['btn_back'].title,
    btnHome: data['btn_home'].title
  }
})

const languages = [
  { label: '日本語', value: 'ja' },
  { label: 'English', value: 'en' },
  { label: '简体中文', value: 'zh' },
  { label: '繁體中文', value: 'zh-tw' },
  { label: '한국어', value: 'ko' }
]
</script>

<template>
  <div class="glass-card">
    <div class="layout-container">
      <!-- 左侧面板：优化比例和背景 -->
      <div class="left-panel">
        <div class="code-badge" :class="errorContent.type">
          <el-icon class="status-icon"><component :is="errorContent.icon" /></el-icon>
          <span class="type-text">{{ errorContent.type.toUpperCase() }}</span>
        </div>
        <h1 class="error-code">{{ code }}</h1>
        <div class="accent-bar" :class="errorContent.type"></div>
      </div>

      <div class="divider-line"></div>

      <!-- 右侧面板：优化排版和字体 -->
      <div class="right-panel">
        <div class="card-lang-tool">
          <el-dropdown trigger="click" @command="(l: string) => emit('change-lang', l)" placement="bottom-end">
            <div class="mini-lang-btn">
              <el-icon class="rotate-icon"><Switch /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="ez-dropdown-menu">
                <el-dropdown-item v-for="lang in languages" :key="lang.value" :command="lang.value">
                  {{ lang.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <section class="info-section">
          <div class="title-group">
            <h2 class="title-main">{{ errorContent.title }}</h2>
            <div class="title-underline"></div>
          </div>
          <div class="message-group">
            <p class="msg-main">{{ errorContent.message }}</p>
          </div>
        </section>

        <div class="actions">
          <el-button @click="emit('back')" :icon="Back" size="large" class="glass-btn">
            {{ errorContent.btnBack }}
          </el-button>
          <el-button type="primary" @click="emit('home')" :icon="HomeFilled" size="large" class="primary-btn">
            {{ errorContent.btnHome }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* --- 核心卡片容器 --- */
.glass-card {
  position: relative;
  width: 100%;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-2xl);
  box-shadow: 0 30px 60px -12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.layout-container { display: flex; flex-direction: row; min-height: 460px; }

/* --- 左侧面板：更精致的布局 --- */
.left-panel {
  flex: 0 0 300px;
  background: rgba(255, 255, 255, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.code-badge {
  display: flex; align-items: center; gap: 8px; padding: 8px 20px; border-radius: var(--radius-round); margin-bottom: 24px;
  font-size: 11px; font-weight: 900; letter-spacing: 1.5px;
}
.code-badge.info { background: #eff6ff; color: #2563eb; }
.code-badge.error { background: #fef2f2; color: #dc2626; }
.code-badge.warning { background: #fffbeb; color: #d97706; }

.error-code {
  font-family: 'Inter', 'JetBrains Mono', sans-serif;
  font-size: 110px; font-weight: 900; line-height: 1; margin: 0;
  background: linear-gradient(180deg, #0f172a 30%, #475569 100%);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  letter-spacing: -4px;
  filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.05));
}

.accent-bar { width: 80px; height: 8px; border-radius: var(--radius-ss); margin-top: 32px; }
.accent-bar.info { background: #3b82f6; box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3); }
.accent-bar.error { background: #ef4444; box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3); }
.accent-bar.warning { background: #f59e0b; box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3); }

/* --- 分割线 --- */
.divider-line {
  width: 1px;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0) 0%, rgba(226, 232, 240, 0.8) 20%, rgba(226, 232, 240, 0.8) 80%, rgba(255, 255, 255, 0) 100%);
}

/* --- 右侧面板：高级感排版 --- */
.right-panel {
  flex: 1; padding: 64px 80px; display: flex; flex-direction: column; justify-content: space-between; min-width: 0; position: relative;
}

.title-group { margin-bottom: 32px; position: relative; }
.title-main {
  font-size: 32px; font-weight: 900; color: #0f172a; margin: 0;
  letter-spacing: -0.5px; line-height: 1.2;
}
.title-underline {
  width: 40px; height: 4px; background: #e2e8f0; border-radius: var(--radius-ss); margin-top: 16px;
}

.message-group { margin-bottom: 48px; }
.msg-main {
  font-size: 17px; color: #475569; line-height: 1.8; margin: 0;
  font-weight: 500;
}

/* --- 语言切换工具 --- */
.card-lang-tool { position: absolute; top: 24px; right: 24px; }
.mini-lang-btn {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  background: rgba(241, 245, 249, 0.8); border-radius: var(--radius-sm); color: #64748b; cursor: pointer; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.mini-lang-btn:hover { background: #fff; color: var(--el-color-primary); box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06); transform: translateY(-1px); }
.rotate-icon { transition: transform 0.5s cubic-bezier(0.4, 0, 0.2, 1); }
.mini-lang-btn:hover .rotate-icon { transform: rotate(180deg); }

/* --- 按钮动作区 --- */
.actions { display: flex; gap: 20px; justify-content: flex-start; }

.glass-btn {
  background: rgba(255, 255, 255, 0.8) !important; border: 1px solid #e2e8f0 !important; border-radius: var(--radius-lg);
  font-weight: 800; letter-spacing: 0.5px; color: #475569 !important; min-width: 140px; height: 52px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); box-shadow: 0 4px 6px rgba(0, 0, 0, 0.02);
}
.glass-btn:hover { background: #fff !important; border-color: #cbd5e1 !important; transform: translateY(-2px); box-shadow: 0 12px 20px rgba(0, 0, 0, 0.05); color: #0f172a !important; }

.primary-btn {
  border-radius: var(--radius-lg); font-weight: 800; letter-spacing: 0.5px; min-width: 140px; height: 52px;
  box-shadow: 0 12px 24px rgba(59, 130, 246, 0.25); transition: all 0.3s ease;
}
.primary-btn:hover { transform: translateY(-2px); box-shadow: 0 16px 32px rgba(59, 130, 246, 0.35); }

/* --- 响应式适配 --- */
@media (max-width: 768px) {
  .layout-container { flex-direction: column; }
  .left-panel { flex: none; padding: 48px 20px; }
  .divider-line { width: 100%; height: 1px; background: linear-gradient(to right, rgba(255, 255, 255, 0) 0%, #e2e8f0 50%, rgba(255, 255, 255, 0) 100%); }
  .right-panel { padding: 48px 32px; text-align: center; align-items: center; }
  .title-underline { margin: 16px auto 0; }
  .error-code { font-size: 80px; }
  .actions { justify-content: center; width: 100%; }
  .glass-btn, .primary-btn { flex: 1; min-width: 0; }
  .card-lang-tool { top: 16px; right: 16px; }
}
</style>
