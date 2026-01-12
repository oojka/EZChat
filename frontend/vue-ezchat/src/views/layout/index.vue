<script setup lang="ts">
/**
 * 主布局组件
 *
 * 功能：
 * - 应用整体布局框架（Header + Aside + Main）
 * - 响应式适配桌面端与移动端
 * - 移动端 Tabbar 导航
 * - 聊天列表快速入口（移动端 /chat 根路由）
 *
 * 布局结构：
 * - MainHeader: 顶部导航栏（仅桌面端显示）
 * - MainAside: 左侧边栏/聊天列表（仅桌面端显示）
 * - RouterView: 主内容区域
 * - MobileTabbar: 底部导航栏（仅移动端显示）
 *
 * 依赖：
 * - appStore: 应用全局状态
 * - useIsMobile: 设备类型检测
 */
import MainHeader from '@/views/layout/sections/MainHeader.vue'
import MainAside from '@/views/layout/sections/MainAside.vue'
import MobileTabbar from '@/views/layout/components/MobileTabbar.vue'
import ChatListView from '@/views/mobile/ChatListView.vue'
import { useAppStore } from '@/stores/appStore.ts'
import { useIsMobile } from '@/composables/useIsMobile'
import { onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'

const appStore = useAppStore()
const { setFavicon, removeFavicon } = appStore
const { isMobile } = useIsMobile()
const route = useRoute()

const showDesktopLayout = computed(() => !isMobile.value)
const showMobileTabbar = computed(() => isMobile.value)

// 移动端 /chat 根路由显示聊天列表而不是欢迎页
const showMobileChatList = computed(() => isMobile.value && route.name === 'Welcome')

onMounted(async () => {
  setFavicon()
})

onUnmounted(() => {
  removeFavicon()
})
</script>

<template>
  <div class="common-layout" :class="{ 'is-mobile': isMobile }">
    <el-container class="outer-container">
      <el-header v-if="showDesktopLayout" class="header desktop-only">
        <MainHeader />
      </el-header>

      <el-container class="inner-container">
        <el-aside v-if="showDesktopLayout" width="350px" class="desktop-only">
          <MainAside />
        </el-aside>

        <el-main class="main-content">
          <div class="main-container">
            <ChatListView v-if="showMobileChatList" />
            <RouterView v-else />
          </div>
        </el-main>
      </el-container>

      <MobileTabbar v-if="showMobileTabbar" />
    </el-container>
  </div>
</template>

<style scoped>
.common-layout {
  /* Inherit fixed size from App.vue */
  height: 100%;
  width: 100%;
  overflow: hidden;
  background-color: var(--bg-page);
}

.outer-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.header {
  height: 60px;
  width: 100%;
  padding: 0;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  z-index: 10;
  background-color: var(--bg-card);
  flex: none;
}

.inner-container {
  /* Remove fixed height calculation, use flex-1 */
  flex: 1;
  overflow: hidden; /* Scroll should happen inside main-content or its children */
  display: flex; /* Allow children to fill height */
}

.common-layout.is-mobile .inner-container {
  /* Ensure it fills available space */
  height: auto;
}

.main-content {
  padding: 0;
  height: 100%;
  overflow: hidden;
}

.main-container {
  height: 100%;
  width: 100%;
}

@media (max-width: 767px) {
  .desktop-only {
    display: none !important;
  }
}
</style>
