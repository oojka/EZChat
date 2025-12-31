<script setup lang="ts">
interface Props {
  size?: number | string
  color?: string
  text?: string
  showText?: boolean
  absolute?: boolean
  showBlobs?: boolean
}

withDefaults(defineProps<Props>(), {
  size: 48,
  color: 'var(--primary)',
  text: '',
  showText: false,
  absolute: false,
  showBlobs: true
})
</script>

<template>
  <div class="app-spinner-container" :class="{ 'is-absolute': absolute }">
    <!-- 同步首页动态背景 -->
    <div v-if="showBlobs" class="bg-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
    </div>

    <div class="spinner-content">
      <div
        class="spinner"
        :style="{
          width: size + 'px',
          height: size + 'px',
          borderTopColor: color
        }"
      ></div>
      <p v-if="showText && text" class="spinner-text">{{ text }}</p>
    </div>
  </div>
</template>

<style scoped>
.app-spinner-container {
  position: fixed; inset: 0; z-index: 9999;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--bg-page);
  background-image:
    radial-gradient(circle at 2px 2px, rgba(255,255,255,0.02) 1px, transparent 0),
    radial-gradient(circle at 50% 50%, var(--primary-light) 0%, transparent 80%),
    radial-gradient(circle at 0% 0%, var(--primary-light) 0%, transparent 70%),
    radial-gradient(circle at 100% 100%, var(--primary-light) 0%, transparent 70%);
  background-size: 40px 40px, 100% 100%, 100% 100%, 100% 100%;
  overflow: hidden;
  transition: background 0.8s var(--ease-out-expo);
}

.app-spinner-container.is-absolute {
  position: absolute;
  background: transparent;
  background-image: none;
}

html.dark .app-spinner-container {
  background: var(--bg-page);
  background-image:
    radial-gradient(circle at 0% 0%, var(--primary-light) 0%, transparent 70%),
    radial-gradient(circle at 100% 100%, var(--primary-light) 0%, transparent 70%);
}

html.dark .app-spinner-container.is-absolute {
  background: transparent;
  background-image: none;
}

/* --- 同步首页背景动效 --- */
.bg-blobs { position: absolute; inset: -50%; z-index: 0; pointer-events: none; animation: global-rotate 150s linear infinite; }
.blob {
  position: absolute; filter: blur(120px); opacity: 0.4;
  transition: all 1s var(--ease-out-expo);
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  animation: blob-breathe 20s ease-in-out infinite;
}
.blob-1 { background: #60a5fa; width: 600px; height: 600px; top: 25%; left: 25%; }
.blob-2 { background: #93c5fd; width: 700px; height: 700px; bottom: 25%; right: 25%; animation-delay: -10s; }

html.dark .blob { filter: blur(180px); }
html.dark .blob-1 { background: #080808; opacity: 0.02; }
html.dark .blob-2 { background: #0a0a0a; opacity: 0.02; }

@keyframes global-rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes blob-breathe {
  0%, 100% { transform: scale(1); border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%; opacity: 0.3; }
  50% { transform: scale(1.5); border-radius: 60% 40% 30% / 70% 50% 60% 40%; opacity: 0.7; }
}

.spinner-content { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; }
.spinner { border: 4px solid var(--el-border-color-light); border-radius: 50%; animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite; }
.spinner-text { font-size: 14px; font-weight: 600; color: var(--text-500); margin-top: 16px; letter-spacing: 1px; text-transform: uppercase; }

@keyframes spin { to { transform: rotate(360deg); } }
</style>
