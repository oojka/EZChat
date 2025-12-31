<script setup lang="ts">
import {computed} from 'vue'
import {useRouter} from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import ErrorContainer from '@/components/ErrorContainer.vue'
import {useI18n} from 'vue-i18n'

const { locale } = useI18n()
const router = useRouter()

const errorCode = computed(() => {
  return (history.state?.code || '404').toString()
})

const handleLanguageChange = (lang: string) => {
  locale.value = lang
  localStorage.setItem('locale', lang)
}

const goHome = () => router.push('/')
const goBack = () => router.go(-1)
</script>

<template>
  <!-- 增加 force-light 类名以强制锁定白天模式 -->
  <div class="error-page-wrapper force-light">
    <div class="bg-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
    </div>

    <div class="content-container">
      <div class="logo-header">
        <!-- 强制 Logo 颜色为深色 -->
        <AppLogo :size="80" color="#0f172a" />
      </div>

      <ErrorContainer
        :code="errorCode"
        :locale="locale"
        @home="goHome"
        @back="goBack"
        @change-lang="handleLanguageChange"
      />
    </div>
  </div>
</template>

<style scoped>
/* --- 核心：强制锁定白天模式变量 --- */
.error-page-wrapper.force-light {
  /* 覆盖全局变量，确保子组件也变回白天模式 */
  --bg-page: #f8fafc;
  --bg-card: #ffffff;
  --text-900: #0f172a;
  --text-700: #334155;
  --text-500: #64748b;
  --text-400: #94a3b8;
  --primary-light: #e0f2fe;
  --border-glass: rgba(255, 255, 255, 0.4);
  --bg-glass: rgba(255, 255, 255, 0.5);
  --shadow-glass: 0 12px 40px 0 rgba(31, 38, 135, 0.05);
  --el-border-color-light: #f1f5f9;

  position: relative; height: 100vh; width: 100vw;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
  font-family: 'Inter', 'Noto Sans JP', sans-serif;
  color: var(--text-900);
}

/* 强制背景动效为白天模式 */
.bg-blobs { position: absolute; inset: -50%; z-index: 0; pointer-events: none; animation: global-rotate 150s linear infinite; }
.blob {
  position: absolute; filter: blur(120px); opacity: 0.4;
  transition: all 1s var(--ease-out-expo);
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  animation: blob-breathe 20s ease-in-out infinite;
}
.blob-1 { background: #60a5fa; width: 600px; height: 600px; top: 25%; left: 25%; }
.blob-2 { background: #93c5fd; width: 700px; height: 700px; bottom: 25%; right: 25%; animation-delay: -10s; }

/* 禁用任何来自 .dark 的覆盖 */
:deep(.dark) .blob { filter: blur(120px) !important; }
:deep(.dark) .blob-1 { background: #60a5fa !important; opacity: 0.4 !important; }
:deep(.dark) .blob-2 { background: #93c5fd !important; opacity: 0.4 !important; }

@keyframes global-rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes blob-breathe {
  0%, 100% { transform: scale(1); border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%; opacity: 0.3; }
  50% { transform: scale(1.5); border-radius: 60% 40% 30% / 70% 50% 60% 40%; opacity: 0.7; }
}

.content-container { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; width: 80%; max-width: 900px; margin-top: -15vh; }
.logo-header { margin-bottom: 60px; }

/* 强制 ErrorContainer 内部样式 */
:deep(.error-container) {
  background: var(--bg-glass) !important;
  backdrop-filter: blur(24px) saturate(180%) !important;
  border: 1px solid var(--border-glass) !important;
  box-shadow: var(--shadow-glass) !important;
}
</style>
