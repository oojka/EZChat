<script setup lang="ts">
/**
 * 应用加载动画组件
 *
 * 功能：
 * - 全屏/定位加载遮罩
 * - 动态背景光球效果
 * - 可选加载文字提示
 *
 * Props：
 * - size: 加载图标尺寸
 * - color: 加载图标颜色
 * - text: 加载提示文字
 * - showText: 是否显示文字
 * - absolute: 是否使用绝对定位
 * - showBlobs: 是否显示背景光球
 * - bgWhite: 是否使用白色背景
 */
interface Props {
  size?: number | string
  color?: string
  text?: string
  showText?: boolean
  absolute?: boolean
  showBlobs?: boolean
  bgWhite?: boolean
}

withDefaults(defineProps<Props>(), {
  size: 48,
  color: 'var(--primary)',
  text: '',
  showText: false,
  absolute: false,
  showBlobs: true,
  bgWhite: false
})
</script>

<template>
  <div
    class="app-spinner-container"
    :class="{ 'is-absolute': absolute, 'bg-white': bgWhite }"
  >
    <!-- 同步首页动态背景 -->
    <div v-if="showBlobs && !bgWhite" class="bg-blobs">
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

.app-spinner-container.bg-white {
  background: #ffffff !important;
  background-image: none !important;
}

.app-spinner-container.is-absolute {
  /* 局部遮蔽：覆盖父容器（父容器需 position: relative）并居中内容 */
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 磨砂玻璃：与全局 Dialog/Notification 风格统一 */
  background: var(--bg-glass-overlay, var(--bg-glass));
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border: 1px solid var(--border-glass);
  box-shadow: var(--shadow-glass);
  border-radius: inherit;
  background-image: none;
  width: 100%;
  height: 100%;
  padding: 0;
}

html.dark .app-spinner-container {
  background: var(--bg-page);
  background-image:
    radial-gradient(circle at 0% 0%, var(--primary-light) 0%, transparent 70%),
    radial-gradient(circle at 100% 100%, var(--primary-light) 0%, transparent 70%);
}

/* 暗黑模式下仍然保持磨砂底色（避免看起来像“全白/全透明”的突兀遮罩） */
html.dark .app-spinner-container.is-absolute {
  background: var(--bg-glass-overlay, var(--bg-glass));
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

.spinner-content { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; gap: 16px; }
.spinner { 
  border: 4px solid var(--el-border-color-light); 
  border-radius: 50%; 
  animation: spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite; 
  flex-shrink: 0;
  display: block;
}
.spinner-text { 
  font-size: 14px; 
  font-weight: 600; 
  color: var(--text-500); 
  letter-spacing: 1px; 
  text-transform: uppercase;
  margin: 0;
  text-align: center;
}

/* 轻量淡入：仅用于局部加载（absolute=true）。全屏遮蔽时不使用淡入，避免闪烁/跳变。 */
.app-spinner-container.is-absolute { animation: fadeIn 0.25s ease; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

@keyframes spin { to { transform: rotate(360deg); } }
</style>
