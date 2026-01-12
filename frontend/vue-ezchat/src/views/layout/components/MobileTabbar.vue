<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatLineRound, User, Setting } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import { useKeyboardVisible } from '@/composables/useKeyboardVisible'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isKeyboardVisible } = useKeyboardVisible()

const isGuest = computed(() => userStore.loginUserInfo?.userType === 'guest')

type TabItem = {
  key: string
  icon: typeof ChatLineRound
  labelKey: string
  path: string
  locked?: boolean
}

const tabs: TabItem[] = [
  { key: 'chat', icon: ChatLineRound, labelKey: 'mobile.tab_chat', path: '/chat' },
  { key: 'friends', icon: User, labelKey: 'mobile.tab_friends', path: '/chat/friends' },
  { key: 'settings', icon: Setting, labelKey: 'mobile.tab_settings', path: '/chat/settings' },
]

const activeTab = computed(() => {
  const path = route.path
  if (path.startsWith('/chat/settings')) return 'settings'
  if (path.startsWith('/chat/friends')) return 'friends'
  return 'chat'
})

const handleTabClick = (tab: TabItem) => {
  if (tab.key === 'friends' && isGuest.value) {
    router.push('/chat/settings?upgrade=true')
    return
  }
  router.push(tab.path)
}

const isTabLocked = (tab: TabItem) => {
  return tab.key === 'friends' && isGuest.value
}
</script>

<template>
  <nav v-show="!isKeyboardVisible" class="mobile-tabbar safe-area-bottom">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="tabbar-item"
      :class="{
        'is-active': activeTab === tab.key,
        'is-locked': isTabLocked(tab)
      }"
      @click="handleTabClick(tab)"
    >
      <div class="tabbar-icon-wrapper">
        <el-icon class="tabbar-icon">
          <component :is="tab.icon" />
        </el-icon>
        <el-icon v-if="isTabLocked(tab)" class="lock-badge">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
          </svg>
        </el-icon>
      </div>
      <span class="tabbar-label">{{ t(tab.labelKey) }}</span>
    </button>
  </nav>
</template>

<style scoped>
.mobile-tabbar {
  /* Use flex-none so it doesn't grow, but sits in the flex column flow */
  flex: none;
  width: 100%;
  height: var(--tabbar-height);
  background: var(--bg-card);
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  justify-content: space-around;
  align-items: center;
  z-index: 1000;
  /* Add safe area padding to the bottom */
  padding-bottom: var(--safe-area-bottom);
  box-sizing: content-box; /* Height + padding */
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 6px 0;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-400);
  transition: color 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}

.tabbar-item.is-active {
  color: var(--primary);
}

.tabbar-item.is-locked {
  color: var(--text-400);
  opacity: 0.7;
}

.tabbar-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tabbar-icon {
  font-size: 22px;
}

.lock-badge {
  position: absolute;
  top: -4px;
  right: -8px;
  font-size: 10px;
  color: var(--text-400);
}

.tabbar-label {
  font-size: 10px;
  font-weight: 500;
  line-height: 1.2;
}

.tabbar-slide-enter-active,
.tabbar-slide-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.tabbar-slide-enter-from,
.tabbar-slide-leave-to {
  transform: translateY(100%);
  opacity: 0;
}
</style>
