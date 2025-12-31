<script setup lang="ts">
import {ref, watch} from 'vue'
import {useChatMemberList} from '@/hooks/useChatMemberList.ts'
import ChatMemberListItem from '@/views/chat/components/ChatMemberListItem.vue'
import {useMessageStore} from '@/stores/messageStore.ts'
import {storeToRefs} from 'pinia'
import AppSpinner from '@/components/AppSpinner.vue'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const messageStore = useMessageStore()
const { chatViewIsLoading } = storeToRefs(messageStore)
const { chat, sortedChatMemberList, loginUserInfo } = useChatMemberList()

const showLocalLoading = ref(chatViewIsLoading.value)
watch(chatViewIsLoading, (newVal) => {
  if (newVal) showLocalLoading.value = true
  else setTimeout(() => { showLocalLoading.value = false }, 100)
}, { immediate: true })
</script>

<template>
  <div class="member-sidebar-wrapper">
    <div class="sidebar-header">
      <div class="header-label">{{ t('chat.members') }}</div>
      <div class="stats-box">
        <span class="online-count">{{ chat?.onLineMemberCount }}</span>
        <span class="separator">/</span>
        <span class="total-count">{{ chat?.memberCount }}</span>
      </div>
    </div>

    <TransitionGroup name="member-list" tag="div" class="member-list-area">
      <ChatMemberListItem v-for="member in sortedChatMemberList" :key="member.uid" :member="member" :is-me="member.uid === loginUserInfo?.uid" />
    </TransitionGroup>

    <Transition name="fade">
      <div v-if="showLocalLoading" class="local-loading-overlay">
        <div class="loading-content">
          <AppSpinner size="28" />
          <p class="loading-text">{{ t('common.loading') }}</p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.member-sidebar-wrapper {
  position: relative; height: 100%; display: flex; flex-direction: column;
  background-color: var(--bg-card);
  overflow: hidden;
  transition: background-color 0.3s ease;
}

.local-loading-overlay {
  position: absolute; inset: 0; z-index: 100;
  background: var(--bg-glass);
  backdrop-filter: blur(12px) saturate(150%);
  display: flex; justify-content: center; align-items: center;
}

.loading-text { font-size: 12px; font-weight: 600; color: var(--text-500); letter-spacing: 0.5px; margin-top: 10px; }
.member-list-area { flex: 1; overflow-y: auto; padding: 0; }

.sidebar-header {
  padding: 20px 24px 12px; display: flex; justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}

.stats-box { display: flex; align-items: baseline; gap: 2px; }
.online-count { font-size: 14px; font-weight: 800; color: var(--primary); }
.separator { font-size: 12px; color: var(--text-400); }
.total-count { font-size: 12px; font-weight: 600; color: var(--text-500); }
.header-label { font-size: 12px; font-weight: 700; color: var(--text-400); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.member-list-enter-active, .member-list-leave-active { transition: all 0.2s ease; }
.member-list-enter-from, .member-list-leave-to { opacity: 0; transform: translateY(-5px); }
.member-list-move { transition: transform 0.2s ease; }
</style>
