<script setup lang="ts">
import { ref } from 'vue'
import {useChatMemberList} from '@/composables/useChatMemberList.ts'
import ChatMemberItem from '@/views/chat/components/ChatMemberItem.vue'
import UserProfileDialog from '@/components/dialogs/UserProfileDialog.vue'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const { chat, sortedChatMemberList, loginUserInfo } = useChatMemberList()

const memberListAreaRef = ref<HTMLElement | null>(null)
const profileVisible = ref(false)
const selectedUid = ref('')

const scrollToTop = () => {
  if (!memberListAreaRef.value) return
  memberListAreaRef.value.scrollTo({ top: 0, behavior: 'smooth' })
}

const handleMemberClick = (uid: string) => {
  selectedUid.value = uid
  profileVisible.value = true
}

defineExpose({
  scrollToTop
})
</script>

<template>
  <div class="member-sidebar-wrapper">
    <div class="sidebar-header">
      <div class="header-label">{{ t('chat.members') }}</div>
      <div class="stats-box">
        <span class="online-count">{{ chat?.onLineMemberCount || 0 }}</span>
        <span class="separator">/</span>
        <span class="total-count">{{ chat?.memberCount || 0 }}</span>
      </div>
    </div>

    <div class="member-list-area" ref="memberListAreaRef">
      <TransitionGroup name="member-list" tag="div">
        <ChatMemberItem 
          v-for="member in sortedChatMemberList" 
          :key="member.uid" 
          :member="member" 
          :is-me="member.uid === loginUserInfo?.uid" 
          @click="handleMemberClick(member.uid)"
        />
      </TransitionGroup>
    </div>

    <UserProfileDialog 
      v-model:visible="profileVisible" 
      :uid="selectedUid" 
    />
  </div>
</template>

<style scoped>
.member-sidebar-wrapper {
  position: relative; height: 100%; display: flex; flex-direction: column;
  background-color: var(--bg-card);
  overflow: hidden;
  transition: background-color 0.3s ease;
}

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

.member-list-enter-active, .member-list-leave-active { transition: all 0.2s ease; }
.member-list-enter-from, .member-list-leave-to { opacity: 0; transform: translateY(-5px); }
.member-list-move { transition: transform 0.2s ease; }
</style>
