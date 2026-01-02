<script setup lang="ts">
import {nextTick, ref, watch} from 'vue'
import {Link, Plus} from '@element-plus/icons-vue'
import ChatItem from '@/views/layout/components/ChatItem.vue'
import CreateChatDialog from '@/components/dialogs/CreateChatDialog.vue'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useAppStore} from '@/stores/appStore.ts'
import {storeToRefs} from 'pinia'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const roomStore = useRoomStore()
const { roomList, currentRoomCode, isRoomListLoading } = storeToRefs(roomStore)
const router = useRouter()
const appStore = useAppStore()
const { createRoomVisible } = storeToRefs(appStore)

// 滚动容器引用
const listContentRef = ref<HTMLElement | null>(null)

const handleSelectChat = (chatCode: string) => {
  router.push(`/chat/${chatCode}`)
}

/**
 * 滚动到当前激活的房间
 * 
 * 业务目的：
 * - 当 currentRoomCode 变化时（例如创建房间后导航），自动滚动到目标房间
 * - 使目标房间在可视区域内居中显示
 */
const scrollToCurrentRoom = async () => {
  // 边界检查：如果列表正在加载，则不执行滚动
  if (isRoomListLoading.value) return
  
  // 等待 DOM 更新完成
  await nextTick()
  
  // 检查滚动容器是否已渲染
  if (!listContentRef.value) return
  
  // 检查是否有当前房间代码
  if (!currentRoomCode.value) return
  
  // 查找目标房间元素
  const targetElement = listContentRef.value.querySelector(
    `[data-chat-code="${currentRoomCode.value}"]`
  ) as HTMLElement | null
  
  // 如果找到目标元素，滚动到该位置
  if (targetElement) {
    targetElement.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
      inline: 'nearest',
    })
  }
}

// 监听 currentRoomCode 变化，自动滚动到目标房间
watch(
  () => currentRoomCode.value,
  (newVal, oldVal) => {
    // 只在值真正变化时滚动（避免初始化时的无效滚动）
    if (newVal && newVal !== oldVal) {
      scrollToCurrentRoom()
    }
  }
)

// 房间列表由 appStore.initializeApp(refresh) 统一触发；此处不再重复请求，避免刷新期间并发打满。
// 表单清空逻辑：在 useCreateChat 的 watch 中，当 createRoomVisible 变为 true 时自动清空表单
</script>

<template>
  <div class="aside-list-container">
    <div class="aside-header">
      <h3 class="aside-title">{{ t('chat.chat_list') }}</h3>
      <div class="action-group">
        <el-button class="header-action-btn" :icon="Link" />
        <el-button class="header-action-btn" :icon="Plus" @click="createRoomVisible = true" />
      </div>
    </div>

    <div class="list-content" ref="listContentRef">
      <!-- 刷新/初始化期间：优先展示 Skeleton，避免“先出现 1 条，再补齐” -->
      <div v-if="isRoomListLoading && (!roomList || roomList.length === 0)" class="chat-skeleton">
        <el-skeleton animated :rows="6" />
      </div>
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

    <CreateChatDialog v-if="createRoomVisible" />
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
</style>
