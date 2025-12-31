<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {Link, Plus} from '@element-plus/icons-vue'
import ChatItem from '@/views/layout/components/ChatItem.vue'
import UserItem from '@/components/UserItem.vue'
import CreateChatDialog from '@/components/dialogs/CreateChatDialog.vue'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useUserStore} from '@/stores/userStore.ts'
import {useWebsocketStore} from '@/stores/websocketStore.ts'
import {storeToRefs} from 'pinia'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const roomStore = useRoomStore()
const { roomList, currentRoomCode } = storeToRefs(roomStore)
const userStore = useUserStore()
const { loginUserInfo } = storeToRefs(userStore)
const websocketStore = useWebsocketStore()
const { status } = storeToRefs(websocketStore)
const router = useRouter()
const showCreateDialog = ref(false)

const handleSelectChat = (chatCode: string) => {
  router.push(`/chat/${chatCode}`)
}

onMounted(async () => {
  await roomStore.initRoomList()
})
</script>

<template>
  <div class="aside-list-container">
    <div class="aside-header">
      <h3 class="aside-title">{{ t('chat.chat_list') }}</h3>
      <div class="action-group">
        <el-button class="header-action-btn" :icon="Link" />
        <el-button class="header-action-btn" :icon="Plus" @click="showCreateDialog = true" />
      </div>
    </div>

    <div class="list-content">
      <div v-if="roomList && roomList.length > 0" class="chat-items">
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

    <div class="aside-footer" v-if="loginUserInfo">
      <UserItem
        :avatar="loginUserInfo.avatar?.objectThumbUrl"
        :nickname="loginUserInfo.nickname"
        :uid="loginUserInfo.uid"
        :is-online="status === 'OPEN'"
        class="footer-user-card"
      />
    </div>

    <CreateChatDialog v-model="showCreateDialog" />
  </div>
</template>

<style scoped>
.aside-list-container {
  height: 100%; display: flex; flex-direction: column;
  background-color: var(--bg-aside);
  border-right: 1px solid var(--el-border-color-light);
  transition: background-color 0.3s ease;
}

.aside-header { padding: 14px 20px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--el-border-color-light); }
.aside-title { font-size: 16px; font-weight: 800; color: var(--text-900); margin: 0; }
.action-group { display: flex; gap: 8px; }
.header-action-btn { width: 32px; height: 32px; padding: 0; background-color: var(--bg-glass); border: none; border-radius: 8px; color: var(--text-500); transition: all 0.2s; }
.header-action-btn:hover { background-color: var(--primary); color: #fff; }

.list-content { flex: 1; overflow-y: auto; }
.empty-state { padding-top: 60px; }

.aside-footer {
  padding: 12px 16px;
  background-color: var(--bg-page);
  border-top: 1px solid var(--el-border-color-light);
}

.footer-user-card {
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
}
</style>
