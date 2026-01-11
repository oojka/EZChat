<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Link, Plus } from '@element-plus/icons-vue'
import ChatItem from '@/views/layout/components/ChatItem.vue'
import CreateChatDialog from '@/components/dialogs/CreateChatDialog.vue'
import JoinChatDialog from '@/components/dialogs/JoinChatDialog.vue'
import FriendList from '@/views/friend/FriendList.vue'
import RequestList from '@/views/friend/RequestList.vue'
import AddFriendDialog from '@/components/AddFriendDialog.vue'
import { useRoomStore } from '@/stores/roomStore.ts'
import { useFriendStore } from '@/stores/friendStore'
import { storeToRefs } from 'pinia'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type ViewType = 'friends' | 'chat'

const props = withDefaults(defineProps<{
  type?: ViewType
}>(), {
  type: 'chat'
})

// Stores
const roomStore = useRoomStore()
const friendStore = useFriendStore()
const { roomList, currentRoomCode, isRoomListLoading, createChatDialogVisible, joinChatDialogVisible } = storeToRefs(roomStore)

const router = useRouter()
const route = useRoute()

const listContentRef = ref<HTMLElement | null>(null)
const addFriendDialogVisible = ref(false)

const handleSelectChat = (chatCode: string) => {
  router.push(`/chat/${chatCode}`)
}

const scrollToCurrentRoom = async (targetCode?: string) => {
  if (isRoomListLoading.value) return
  const code = targetCode || currentRoomCode.value
  if (!code) return
  await nextTick()
  if (!listContentRef.value) return
  const targetElement = listContentRef.value.querySelector(
    `[data-chat-code="${code}"]`
  ) as HTMLElement | null
  if (targetElement) {
    targetElement.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
      inline: 'nearest',
    })
  }
}

watch(
  () => currentRoomCode.value,
  (newVal, oldVal) => {
    if (props.type !== 'chat') return
    if (newVal && newVal !== oldVal) {
      scrollToCurrentRoom()
    }
  }
)

watch(
  () => route.params.chatCode,
  (newCode) => {
    if (props.type === 'chat' && newCode && typeof newCode === 'string') {
      scrollToCurrentRoom(newCode)
    }
  }
)

watch(
  () => isRoomListLoading.value,
  (isLoading) => {
    if (!isLoading && props.type === 'chat') {
      scrollToCurrentRoom()
    }
  }
)

// Fetch friends when tab switches
watch(
  () => props.type,
  (newType) => {
    if (newType === 'friends') {
      friendStore.fetchFriends()
      friendStore.fetchRequests()
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="aside-list-container">

    <!-- Chat View -->
    <div v-if="type === 'chat'" class="view-content">
      <div class="aside-header">
        <h3 class="aside-title">{{ t('chat.chat_list') }}</h3>
        <div class="action-group">
          <el-button class="header-action-btn" :icon="Link" @click="joinChatDialogVisible = true" />
          <el-button class="header-action-btn" :icon="Plus" @click="createChatDialogVisible = true" />
        </div>
      </div>

      <div class="list-content" ref="listContentRef">
        <div v-if="isRoomListLoading && (!roomList || roomList.length === 0)" class="chat-skeleton">
          <el-skeleton animated :rows="6" />
        </div>
        <div v-else-if="roomList && roomList.length > 0" class="chat-items">
          <ChatItem v-for="chat in roomList" :key="chat.chatCode" :chat="chat"
            :is-active="chat.chatCode === currentRoomCode" @click="handleSelectChat(chat.chatCode)" />
        </div>
        <div v-else class="empty-state">
          <el-empty :description="t('aside.no_chats')" :image-size="80" />
        </div>
      </div>

      <CreateChatDialog v-if="createChatDialogVisible" />
      <JoinChatDialog v-if="joinChatDialogVisible" />
    </div>

    <!-- Friend View -->
    <div v-else-if="type === 'friends'" class="view-content">
      <div class="aside-header">
        <h3 class="aside-title">{{ t('aside.friends_view') }}</h3>
        <div class="action-group">
          <el-button class="header-action-btn" :icon="Plus" @click="addFriendDialogVisible = true" />
        </div>
      </div>

      <div class="list-content">
        <RequestList />
        <FriendList />
      </div>
      
      <AddFriendDialog v-model:visible="addFriendDialogVisible" />
    </div>
  </div>
</template>

<style scoped>
.aside-list-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-aside);
  border-right: 1px solid var(--el-border-color-light);
  transition: background-color 0.3s ease;
}

.view-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.aside-header {
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}

.aside-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-900);
  margin: 0;
}

.action-group {
  display: flex;
  gap: 8px;
}

.header-action-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  background-color: var(--bg-glass);
  border: none;
  border-radius: 8px;
  color: var(--text-500);
  transition: all 0.2s;
}

.header-action-btn:hover {
  background-color: var(--primary);
  color: #fff;
}

.list-content {
  flex: 1;
  overflow-y: auto;
}

.empty-state {
  padding-top: 60px;
}
</style>
