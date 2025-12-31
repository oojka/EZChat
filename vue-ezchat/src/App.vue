<script setup lang="ts">
import {watch} from 'vue'
import {useRoute} from 'vue-router'
import {storeToRefs} from 'pinia'
import {useAppStore} from '@/stores/appStore.ts'
import AppSpinner from '@/components/AppSpinner.vue'

const route = useRoute()
const appStore = useAppStore()
const { isAppLoading, showLoadingSpinner, loadingText } = storeToRefs(appStore)

watch(
  () => route.meta.title,
  (newTitle) => {
    if (newTitle === 'Loading...') document.body.classList.add('show-bg')
    else document.body.classList.remove('show-bg')
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-root">
    <RouterView />

    <!-- 全局统一 Loading 遮罩层 -->
    <Transition name="loading-fade">
      <div v-if="isAppLoading" class="global-loading-overlay">
        <AppSpinner v-if="showLoadingSpinner" :text="loadingText" show-text />
      </div>
    </Transition>
  </div>
</template>

<style>
.app-root { height: 100vh; width: 100vw; }

.global-loading-overlay {
  position: fixed; inset: 0; z-index: 99999;
  background: var(--bg-page); /* 适配暗黑模式 */
  display: flex; justify-content: center; align-items: center;
  transition: background-color 0.3s ease;
}

.loading-fade-enter-active, .loading-fade-leave-active { transition: opacity 0.3s ease; }
.loading-fade-enter-from, .loading-fade-leave-to { opacity: 0; }

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
