<script setup lang="ts">
/**
 * 首页入口组件
 *
 * 功能：
 * - 响应式布局适配（桌面端/移动端）
 * - 自动切换显示桌面端首页或移动端欢迎页
 * - 管理 Favicon 生命周期
 *
 * 依赖：
 * - useIsMobile: 设备类型检测
 * - appStore: 应用全局状态
 */
import { computed } from 'vue'
import { useIsMobile } from '@/composables/useIsMobile'
import DesktopIndexView from '@/views/index/index.vue'
import MobileWelcomeView from '@/views/mobile/entry/MobileWelcomeView.vue'
import { useAppStore } from '@/stores/appStore.ts'
import { onMounted, onUnmounted } from 'vue'

const appStore = useAppStore()
const { setFavicon, removeFavicon } = appStore
const { isMobile } = useIsMobile()

const showMobile = computed(() => isMobile.value)

onMounted(() => {
  setFavicon()
})

onUnmounted(() => {
  removeFavicon()
})
</script>

<template>
  <component :is="showMobile ? MobileWelcomeView : DesktopIndexView" />
</template>

<style scoped>
/* No additional styles needed - each component handles its own styling */
</style>