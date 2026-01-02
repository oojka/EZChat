<script setup lang="ts">
import {ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {storeToRefs} from 'pinia'
import {useAppStore} from '@/stores/appStore.ts'
import AppSpinner from '@/components/AppSpinner.vue'

const route = useRoute()
const appStore = useAppStore()
const { isAppLoading, showLoadingSpinner, loadingText } = storeToRefs(appStore)

/**
 * 全局遮蔽层显示控制（与 Store 解耦）
 *
 * 业务目的：
 * - 当全屏 Loading 结束时，使用 View Transitions API 做“淡出并移除”
 * - 避免仅靠 Vue Transition 在某些设备/浏览器上出现闪烁或过渡被打断
 */
const showGlobalOverlay = ref<boolean>(isAppLoading.value)
const prevIsAppLoading = ref<boolean>(isAppLoading.value)

/**
 * 触发 View Transitions API（浏览器不支持则降级执行）
 *
 * 注意：
 * - startViewTransition 是实验性 API，因此这里做能力检测并用 any 兜底类型
 */
const runViewTransition = (applyDomChange: () => void) => {
  const doc = document as any
  if (typeof doc.startViewTransition === 'function') {
    doc.startViewTransition(applyDomChange)
  } else {
    applyDomChange()
  }
}

watch(
  () => route.meta.title,
  (newTitle) => {
    if (newTitle === 'Loading...') document.body.classList.add('show-bg')
    else document.body.classList.remove('show-bg')
  },
  { immediate: true },
)

watch(
  () => isAppLoading.value,
  (val) => {
    if (val) {
      // Loading 开始：立即展示（避免首帧空白）
      showGlobalOverlay.value = true
      prevIsAppLoading.value = true
      return
    }
    // Loading 结束：只在“从 true -> false”时触发 View Transition（避免首帧/重复触发导致页面乱闪）
    const wasLoading = prevIsAppLoading.value
    prevIsAppLoading.value = false
    if (!wasLoading) {
      showGlobalOverlay.value = false
      return
    }
    runViewTransition(() => { showGlobalOverlay.value = false })
  },
  // 不需要 immediate：避免页面首次加载时触发一次无意义的 View Transition
)
</script>

<template>
  <div class="app-root">
    <RouterView />

    <!-- 全局统一 Loading 遮罩层 -->
    <div v-if="showGlobalOverlay" class="global-loading-overlay">
      <AppSpinner v-if="showLoadingSpinner" :text="loadingText" show-text />
    </div>
  </div>
</template>

<style>
.app-root { height: 100vh; width: 100vw; }

.global-loading-overlay {
  position: fixed; inset: 0; z-index: 99999;
  background: var(--bg-page); /* 适配暗黑模式 */
  display: flex; justify-content: center; align-items: center;
  transition: background-color 0.3s ease;
  /* View Transitions API：给遮蔽层命名，便于定义离场动画 */
  view-transition-name: global-loading-overlay;
}

/* 禁用 View Transitions API 对整个页面(root)的默认过渡，否则会把整个 UI 一起做截屏交叉淡入淡出，导致“全乱了” */
::view-transition-old(root),
::view-transition-new(root) {
  animation: none;
}

/* View Transitions API：仅定义“离场淡出”，入场不额外动画（避免首屏闪烁） */
::view-transition-old(global-loading-overlay) {
  animation: global-overlay-fade-out 220ms ease forwards;
}
::view-transition-new(global-loading-overlay) {
  animation: none;
}

@keyframes global-overlay-fade-out {
  from { opacity: 1; }
  to { opacity: 0; }
}

/* 背景球适配 */
body.show-bg::before, body.show-bg::after {
  content: ''; position: absolute; width: 500px; height: 500px; border-radius: 50%;
  filter: blur(80px); z-index: 0; opacity: 0.4; pointer-events: none;
  transition: background-color 0.5s ease;
}
body.show-bg::before { background: var(--primary); top: -100px; left: -100px; }
body.show-bg::after { background: #d9ecff; bottom: -100px; right: -100px; animation-delay: -10s; }
html.dark body.show-bg::after { background: #1e293b; }

@keyframes float { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(40px, 60px); } }
</style>
