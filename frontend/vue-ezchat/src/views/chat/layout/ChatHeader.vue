<script setup lang="ts">
import {storeToRefs} from 'pinia'
import {useRoomStore} from '@/stores/roomStore.ts'
import {InfoFilled, MoreFilled, UserFilled} from '@element-plus/icons-vue'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const roomStore = useRoomStore()
const { currentRoom } = storeToRefs(roomStore)
</script>

<template>
  <div class="chat-header-container">
    <div class="header-left">
      <div class="room-info" v-if="currentRoom">
        <el-avatar :size="40" :src="currentRoom.avatar?.imageThumbUrl" class="room-avatar">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <div class="text-info">
          <div class="name-row">
            <h2 class="room-name">{{ currentRoom.chatName }}</h2>
            <span class="member-count">({{ currentRoom.memberCount }})</span>
          </div>
          <div class="room-status">
            <span class="id-label">{{ t('chat.room_id') }}</span>
            <span class="room-id">{{ currentRoom.chatCode }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="header-right">
      <div class="action-group">
        <el-tooltip :content="t('chat.room_info')" placement="bottom" :show-after="300">
          <div class="action-btn"><el-icon><InfoFilled /></el-icon></div>
        </el-tooltip>
        <el-tooltip :content="t('common.menu')" placement="bottom" :show-after="300">
          <div class="action-btn"><el-icon><MoreFilled /></el-icon></div>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-header-container {
  height: 100%; display: flex; justify-content: space-between; align-items: center;
  padding: 0 24px;
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light); /* 核心：使用变量适配分割线 */
  transition: all 0.3s ease;
}

.header-left { display: flex; align-items: center; }
.room-info { display: flex; align-items: center; gap: 14px; }
.room-avatar { border-radius: calc(40px * var(--avatar-border-radius-ratio)); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); border: 1px solid var(--el-border-color-light); }

.text-info { display: flex; flex-direction: column; gap: 1px; }
.name-row { display: flex; align-items: baseline; gap: 6px; }
.room-name { font-size: 16px; font-weight: 800; color: var(--text-900); margin: 0; }
.member-count { font-size: 13px; color: var(--text-500); font-weight: 600; }

.room-status { display: flex; align-items: center; gap: 4px; }
.id-label {
  font-size: 9px; font-weight: 800; color: var(--text-400);
  background: var(--bg-page);
  padding: 1px 4px; border-radius: 4px; letter-spacing: 0.5px; line-height: 1;
}
.room-id { font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace; font-size: 11px; color: var(--text-500); font-weight: 600; letter-spacing: 1px; text-transform: uppercase; }

.header-right { display: flex; align-items: center; }
.action-group { display: flex; gap: 8px; }
.action-btn {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border-radius: 10px; color: var(--text-500); cursor: pointer; transition: all 0.2s;
}
.action-btn:hover { background-color: var(--bg-page); color: var(--primary); }
.action-btn .el-icon { font-size: 20px; }
</style>
