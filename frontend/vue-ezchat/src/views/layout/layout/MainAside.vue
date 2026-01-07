<script setup lang="ts">
import { ref } from 'vue'
import { ChatLineRound, User } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import AsideList from '@/views/layout/components/AsideList.vue'
import GuestAside from '@/views/layout/components/GuestAside.vue'
import UserItem from '@/components/UserItem.vue'
import { useUserStore } from '@/stores/userStore.ts'
import { useWebsocketStore } from '@/stores/websocketStore.ts'

const { t } = useI18n()

// 当前激活的视图类型：'friends' | 'chat'
const activeView = ref<'friends' | 'chat'>('chat')

const switchView = (view: 'friends' | 'chat') => {
  activeView.value = view
}

// 用户信息相关
const userStore = useUserStore()
const websocketStore = useWebsocketStore()
</script>

<template>
  <div class="main-aside-wrapper">
    <GuestAside v-if="userStore.loginUserInfo?.userType === 'guest'" />
    <!-- 1. 视图切换区 -->
    <div class="switcher-container" v-if="userStore.loginUserInfo?.userType === 'formal'">
      <div class="segmented-control">
        <div class="control-item" :class="{ 'is-active': activeView === 'friends' }" @click="switchView('friends')">
          <el-icon>
            <User />
          </el-icon>
          <span>{{ t('aside.friends_view') }}</span>
        </div>
        <div class="control-item" :class="{ 'is-active': activeView === 'chat' }" @click="switchView('chat')">
          <el-icon>
            <ChatLineRound />
          </el-icon>
          <span>{{ t('aside.chat_view') }}</span>
        </div>
        <!-- 滑动背景指示器 -->
        <div class="selection-indicator" :class="activeView"></div>
      </div>
    </div>

    <!-- 2. 内容展示区 -->
    <div class="aside-body" v-if="userStore.loginUserInfo?.userType === 'formal'">
      <AsideList :type="activeView" />
    </div>

    <!-- 3. 用户信息区域 -->
    <div class="aside-footer">
      <UserItem
        :avatar="userStore.loginUserInfo?.avatar?.blobThumbUrl || userStore.loginUserInfo?.avatar?.imageThumbUrl || userStore.loginUserInfo?.avatar?.blobUrl || userStore.loginUserInfo?.avatar?.imageUrl"
        :nickname="userStore.loginUserInfo?.nickname" :uid="userStore.loginUserInfo?.uid"
        :is-online="websocketStore.status === 'OPEN'" class="footer-user-card" />
    </div>
  </div>
</template>

<style scoped>
.main-aside-wrapper {
  height: 100%;
  background-color: var(--bg-aside);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: background-color 0.3s ease;
}

.switcher-container {
  padding: 16px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.segmented-control {
  position: relative;
  display: flex;
  /* 使用更深一点的容器底色，产生凹陷感 */
  background-color: var(--el-fill-color-blank);
  padding: 4px;
  border-radius: var(--radius-base);
  height: 36px;
  border: 1px solid var(--el-border-color-extra-light);
  transition: all 0.3s ease;
}

.control-item {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-500);
  cursor: pointer;
  transition: color 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.control-item.is-active {
  color: var(--text-900);
}

.control-item:not(.is-active):hover {
  color: var(--text-700);
}

.control-item .el-icon {
  font-size: 14px;
}

.selection-indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: calc(100% - 8px);
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  /* 强化阴影，使其在深色背景下依然立体 */
  box-shadow: var(--shadow-glass);
  border: 1px solid var(--border-glass);
  transition: transform 0.4s var(--ease-out-expo), background-color 0.3s ease;
  z-index: 1;
}

.selection-indicator.chat {
  transform: translateX(100%);
}

.aside-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.aside-footer {
  padding: 12px 16px;
  background-color: var(--bg-page);
  border-top: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.footer-user-card {
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
}
</style>
