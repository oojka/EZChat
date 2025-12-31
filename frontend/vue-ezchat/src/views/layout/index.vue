<script setup lang="ts">
import MainHeader from '@/views/layout/layout/MainHeader.vue'
import MainAside from '@/views/layout/layout/MainAside.vue'
import {useAppStore} from '@/stores/appStore.ts'
import {storeToRefs} from 'pinia'
import {onMounted} from 'vue'

const appStore = useAppStore()
const { isAppLoading, showLoadingSpinner } = storeToRefs(appStore)
const { initializeApp } = appStore

onMounted(async () => {
  // 刷新页面时的初始化：开启全局加载，不显示转圈
  showLoadingSpinner.value = false
  await initializeApp()
})
</script>

<template>
  <div class="common-layout">
    <el-container class="outer-container" v-if="!isAppLoading">
      <el-header class="header">
        <MainHeader />
      </el-header>
      <el-container class="inner-container">
        <el-aside width="350px">
          <MainAside />
        </el-aside>
        <el-main class="main-content">
          <div class="main-container">
            <RouterView></RouterView>
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
