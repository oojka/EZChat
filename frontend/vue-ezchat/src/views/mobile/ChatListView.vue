<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { useUserStore } from '@/stores/userStore'
import { storeToRefs } from 'pinia'
import { Link, Plus } from '@element-plus/icons-vue'
import ChatItem from '@/views/layout/components/ChatItem.vue'
import CreateChatDialog from '@/components/dialogs/CreateChatDialog.vue'
import JoinChatDialog from '@/components/dialogs/JoinChatDialog.vue'
import GuestAside from '@/views/layout/components/GuestAside.vue'

const { t } = useI18n()
const router = useRouter()
const roomStore = useRoomStore()
const userStore = useUserStore()

const { roomList, currentRoomCode, isRoomListLoading, createChatDialogVisible, joinChatDialogVisible } = storeToRefs(roomStore)

const isGuest = computed(() => userStore.loginUserInfo?.userType === 'guest')

const handleSelectChat = (chatCode: string) => {
  router.push(`/chat/${chatCode}`)
}

onMounted(() => {
  // 确保房间列表已加载
  if (!roomList.value || roomList.value.length === 0) {
    roomStore.initRoomList()
  }
})
</script>

<template>
  <div class="mobile-chat-list-view">
    <!-- 访客升级提示 -->
    <GuestAside v-if="isGuest" class="guest-banner" />

    <!-- 页面头部 -->
    <div class="mobile-page-header">
      <h1 class="page-title">{{ t('chat.chat_list') }}</h1>
      <div class="action-group">
        <el-button class="header-action-btn" :icon="Link" @click="joinChatDialogVisible = true" />
        <el-button class="header-action-btn" :icon="Plus" @click="createChatDialogVisible = true" />
      </div>
    </div>

    <!-- 聊天列表 -->
    <div class="chat-list-content">
      <div v-if="isRoomListLoading && (!roomList || roomList.length === 0)" class="chat-skeleton">
        <el-skeleton animated :rows="6" />
      </div>
      <div v-else-if="roomList && roomList.length > 0" class="chat-items">
        <ChatItem
          v-for="chat in roomList"
          :key="chat.chatCode"
          :chat="chat"
          :is-active="chat.chatCode === currentRoomCode"
          @click="handleSelectChat(chat.chatCode)"
        />
      </div>
      <div v-else class="empty-state">
        <el-empty :description="t('aside.no_chats')" :image-size="80" />
      </div>
    </div>

    <!-- 对话框 -->
    <CreateChatDialog v-if="createChatDialogVisible" />
    <JoinChatDialog v-if="joinChatDialogVisible" />
  </div>
</template>

<style scoped>
.mobile-chat-list-view {
  /* Use flex architecture: height auto to fill container */
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  /* Remove fixed padding as Tabbar is now in flex flow */
  /* padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom)); */
  box-sizing: border-box;
}

.guest-banner {
  flex-shrink: 0;
}

.mobile-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.action-group {
  display: flex;
  gap: 8px;
}

.header-action-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  background-color: var(--bg-glass);
  border: none;
  border-radius: 10px;
  color: var(--text-500);
  transition: all 0.2s;
}

.header-action-btn:hover,
.header-action-btn:active {
  background-color: var(--primary);
  color: #fff;
}

.chat-list-content {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.chat-skeleton {
  padding: 16px;
}

.chat-items {
  padding: 8px 0;
}

.empty-state {
  padding-top: 60px;
}
</style>
