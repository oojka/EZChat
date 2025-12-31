<script setup lang="ts">
import {nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {ArrowDown, Loading} from '@element-plus/icons-vue'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useMessageStore} from '@/stores/messageStore.ts'
import {useUserStore} from '@/stores/userStore.ts'
import MessageItem from '@/views/chat/components/MessageItem.vue'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const messageStore = useMessageStore()
const { currentMessageList, loadingMessages, noMoreMessages } = storeToRefs(messageStore)
const { loadMoreHistory } = messageStore

const roomStore = useRoomStore()
const { currentRoom, currentRoomCode } = storeToRefs(roomStore)

const userStore = useUserStore()
const { loginUserInfo } = storeToRefs(userStore)

const listRef = ref<HTMLElement | null>(null)
const loadTrigger = ref<HTMLElement | null>(null)

const unreadNewMessagesCount = ref(0)
const isAtBottom = ref(true)

const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTo({ top: 0, behavior: 'smooth' })
    unreadNewMessagesCount.value = 0
  }
}

const handleScroll = () => {
  if (!listRef.value) return
  const scrollPos = Math.abs(listRef.value.scrollTop)
  isAtBottom.value = scrollPos < 10
  if (isAtBottom.value) unreadNewMessagesCount.value = 0
}

const handleLoadHistory = async () => {
  if (loadingMessages.value || noMoreMessages.value) return
  await loadMoreHistory()
}

let observer: IntersectionObserver | null = null
const setupObserver = () => {
  if (observer) observer.disconnect()
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting && !loadingMessages.value && !noMoreMessages.value) handleLoadHistory()
  }, { root: listRef.value, threshold: 0.1 })
  if (loadTrigger.value) observer.observe(loadTrigger.value)
}

onMounted(() => {
  setupObserver()
  listRef.value?.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  if (observer) observer.disconnect()
  listRef.value?.removeEventListener('scroll', handleScroll)
})

watch(() => currentRoomCode.value, async (newVal) => {
  if (newVal) {
    await nextTick()
    if (listRef.value) listRef.value.scrollTop = 0
    unreadNewMessagesCount.value = 0
    setupObserver()
  }
})

watch(() => currentMessageList.value.length, (newLen, oldLen) => {
  if (newLen > oldLen && currentMessageList.value.length > 0) {
    const latestMsg = currentMessageList.value[0]
    if (latestMsg.sender === loginUserInfo.value?.uid) scrollToBottom()
    else {
      if (isAtBottom.value) scrollToBottom()
      else unreadNewMessagesCount.value++
    }
  }
})
</script>

<template>
  <div class="message-area">
    <ul class="list" ref="listRef">
      <MessageItem v-for="msg in currentMessageList" :key="msg.tempId || `${msg.sender}_${msg.createTime}`" :msg="msg" :current-chat="currentRoom" />
      <li class="load-indicator" ref="loadTrigger">
        <div v-if="loadingMessages" class="loading-wrapper">
          <el-icon class="is-loading" :size="16"><Loading /></el-icon>
          <span>{{ t('common.loading') }}</span>
        </div>
        <el-divider v-else-if="noMoreMessages" content-position="center" class="no-more-divider">
          <span class="no-more-text">{{ t('chat.no_more') }}</span>
        </el-divider>
        <div v-else style="height: 1px; width: 100%"></div>
      </li>
    </ul>
    <Transition name="slide-up">
      <div v-if="unreadNewMessagesCount > 0" class="new-message-tip" @click="scrollToBottom">
        <el-icon class="tip-icon"><ArrowDown /></el-icon>
        <span class="tip-text">{{ unreadNewMessagesCount }} {{ t('chat.new_messages_count') }}</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.message-area {
  height: 100%;
  background-color: var(--bg-page); /* 使用变量适配暗黑模式 */
  box-sizing: border-box;
  position: relative;
  transition: background-color 0.3s ease;
}

.list {
  display: flex; flex-direction: column-reverse; height: 100%; overflow-y: auto; overflow-x: hidden;
  padding: 24px 20px; margin: 0; list-style: none; box-sizing: border-box; gap: 18px; scroll-behavior: smooth;
}

.new-message-tip {
  position: absolute; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: var(--primary);
  backdrop-filter: blur(8px); color: #fff; padding: 8px 16px; border-radius: 20px;
  display: flex; align-items: center; gap: 8px; cursor: pointer;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.3); z-index: 100;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.loading-wrapper { display: flex; gap: 8px; color: var(--text-500); font-size: 13px; }
.no-more-text { color: var(--text-400); font-size: 12px; }

.no-more-divider :deep(.el-divider__text) {
  background-color: var(--bg-page) !important;
  color: var(--text-400);
  transition: background-color 0.3s ease;
}

.list::-webkit-scrollbar { width: 4px; }
.list::-webkit-scrollbar-thumb { background: var(--text-400); border-radius: 10px; opacity: 0.2; }
</style>
