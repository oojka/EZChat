<script setup lang="ts">
import MainHeader from '@/views/layout/layout/MainHeader.vue'
import MainAside from '@/views/layout/layout/MainAside.vue'
import {useAppStore} from '@/stores/appStore.ts'
import {onMounted, onUnmounted} from 'vue'

const appStore = useAppStore()
const { initializeApp, setFavicon, removeFavicon } = appStore

onMounted(async () => {
  // 刷新页面时的初始化：由 App.vue 统一展示全局 Loading 遮罩与 Spinner
  await initializeApp()
  setFavicon()
})

onUnmounted(() => {
  removeFavicon()
})
</script>

<template>
  <div class="common-layout">
    <!-- 不要在此处用 v-if 卸载布局：避免刷新初始化期间反复 mount 导致数据/订阅状态异常 -->
    <el-container class="outer-container">
      <el-header class="header">
        <MainHeader />
      </el-header>
      <el-container class="inner-container">
        <el-aside width="350px">
          <MainAside />
        </el-aside>
        <el-main class="main-content">
          <div class="main-container">
            <!-- Chat 视图的骨架/加载表现由 chat/index.vue 自己控制（左侧 MessageSkeleton + 右侧 AppSpinner） -->
            <RouterView />
          </div>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.common-layout {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background-color: #f4f4f5;
}

.outer-container { height: 100%; }

.header {
  height: 60px;
  width: 100%;
  padding: 0;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  z-index: 10;
  background-color: #fff;
}

.inner-container { height: calc(100vh - 60px); overflow: hidden; }
.main-content { padding: 0; height: 100%; overflow: hidden; }
.main-container { height: 100%; width: 100%; }
</style>
