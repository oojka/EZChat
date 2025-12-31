<script setup lang="ts">
import {UserFilled} from '@element-plus/icons-vue'

interface Props {
  avatar?: string
  nickname?: string
  uid?: string | number
  isOnline?: boolean
  showBadge?: boolean
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  avatar: '', nickname: 'User', uid: '00000000', isOnline: false, showBadge: true, clickable: true
})

const emit = defineEmits<{ (e: 'click'): void }>()
</script>

<template>
  <div class="user-item-wrapper" :class="{ 'is-offline': !isOnline, 'is-clickable': clickable }" @click="clickable && emit('click')">
    <div class="avatar-section">
      <el-badge is-dot :offset="[-2, 34]" :type="isOnline ? 'success' : 'info'" :disabled="!showBadge">
        <el-avatar :size="40" :src="avatar" class="user-avatar"><el-icon><UserFilled /></el-icon></el-avatar>
      </el-badge>
    </div>
    <div class="info-section">
      <div class="name-row"><span class="nickname">{{ nickname }}</span></div>
      <div class="uid-row"><span class="id-label">UID</span><span class="uid-text">{{ uid }}</span></div>
    </div>
  </div>
</template>

<style scoped>
.user-item-wrapper { display: flex; align-items: center; padding: 10px 16px; border-radius: 12px; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); user-select: none; background: transparent; }
.user-item-wrapper.is-clickable:hover { background-color: var(--bg-page); transform: translateX(4px); }
.is-offline { opacity: 0.7; }
.avatar-section { margin-right: 14px; flex-shrink: 0; }
.user-avatar { background-color: var(--bg-page); border: 2px solid var(--bg-card); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.info-section { flex: 1; display: flex; flex-direction: column; justify-content: center; min-width: 0; }
.nickname { font-size: 14px; font-weight: 700; color: var(--text-900); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.uid-row { display: flex; align-items: center; gap: 4px; }
.id-label { font-size: 8px; font-weight: 800; color: var(--text-400); background: var(--bg-page); padding: 0px 3px; border-radius: 3px; letter-spacing: 0.5px; line-height: 1.2; border: 1px solid var(--el-border-color-light); }
.uid-text { font-family: 'JetBrains Mono', monospace; font-size: 10px; color: var(--text-500); font-weight: 600; letter-spacing: 0.5px; }
.is-offline .user-avatar { filter: grayscale(0.5); }
.is-offline .nickname { color: var(--text-500); }
</style>
