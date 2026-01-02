<script setup lang="ts">
import type {ChatMember} from '@/type'
import {useWebsocketStore} from '@/stores/websocketStore.ts'
import {storeToRefs} from 'pinia'
import SmartAvatar from '@/components/SmartAvatar.vue'

interface Props { member: ChatMember; isMe: boolean }
defineProps<Props>()

const websocketStore = useWebsocketStore()
const { wsDisplayState } = storeToRefs(websocketStore)
</script>

<template>
  <div class="member-item" :class="{ 'is-me': isMe, 'is-offline': !member.online }">
    <div class="avatar-wrapper">
      <el-badge is-dot :offset="[-2, 34]" :type="isMe ? wsDisplayState.type : (member.online ? 'success' : 'info')" class="status-dot">
        <SmartAvatar
          class="member-avatar"
          :size="40"
          shape="square"
          :thumb-url="member.avatar.blobThumbUrl || member.avatar.objectThumbUrl"
          :url="member.avatar.blobUrl || member.avatar.objectUrl"
          :text="member.nickname"
        />
      </el-badge>
    </div>

    <div class="member-info">
      <div class="name-row">
        <span class="nickname">{{ member.nickname }}</span>
        <span v-if="isMe" class="me-tag">YOU</span>
      </div>
      <div class="uid-row">
        <span class="id-label">UID</span>
        <span class="uid-text">{{ member.uid }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.member-item {
  display: flex; align-items: center; padding: 10px 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default; user-select: none;
  border-left: 3px solid transparent;
}
.member-item:hover { background-color: var(--bg-page); }

.is-me { background-color: var(--primary-light); border-left-color: var(--primary); }
.is-offline { opacity: 0.5; }

.avatar-wrapper { margin-right: 14px; position: relative; }
.member-avatar { background-color: var(--bg-page); border: 2px solid var(--bg-card); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }
.is-offline .member-avatar { filter: grayscale(1); }

:deep(.el-badge__content.is-dot) {
  width: 12px; height: 12px; border: 2px solid var(--bg-card);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  transition: background-color 0.3s ease;
}

/* 优化在线状态点在暗黑模式下的发光感 */
:deep(.el-badge__content--success) {
  box-shadow: 0 0 8px rgba(103, 194, 58, 0.5);
}

.member-info { flex: 1; display: flex; flex-direction: column; justify-content: center; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.nickname { font-size: 14px; font-weight: 700; color: var(--text-900); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.is-offline .nickname { color: var(--text-500); }

.uid-row { display: flex; align-items: center; gap: 4px; }
.id-label { font-size: 8px; font-weight: 800; color: var(--text-400); background: var(--bg-page); padding: 0px 3px; border-radius: var(--radius-ss); letter-spacing: 0.5px; line-height: 1.2; border: 1px solid var(--el-border-color-light); }
.uid-text { font-family: 'JetBrains Mono', monospace; font-size: 10px; color: var(--text-500); font-weight: 600; letter-spacing: 0.5px; }

.me-tag { font-size: 9px; padding: 1px 6px; background: var(--primary); color: #fff; border-radius: var(--radius-round); font-weight: 900; letter-spacing: 0.5px; flex-shrink: 0; box-shadow: 0 2px 4px rgba(59, 130, 246, 0.2); }
</style>
