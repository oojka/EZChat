<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRoomStore } from '@/stores/roomStore.ts'
import { MoreFilled, User } from '@element-plus/icons-vue'
import Avatar from '@/components/Avatar.vue'
import { useI18n } from 'vue-i18n'
import { useChatRoomActions } from '@/composables/useChatRoomActions'
import RoomSettingsDialog from '@/components/dialogs/room-settings/index.vue'

/** Props */
withDefaults(defineProps<{
  isMobile?: boolean
}>(), {
  isMobile: false,
})

/** Emits */
const emit = defineEmits<{
  openMemberDrawer: []
}>()

const { t } = useI18n()
const roomStore = useRoomStore()
const { currentRoom, roomSettingsDialogVisible } = storeToRefs(roomStore)
const { isOwner, canLeave, canDisband, confirmLeave, confirmDisband } = useChatRoomActions()

/** 移动端：打开成员列表抽屉 */
const handleOpenMemberDrawer = () => {
  emit('openMemberDrawer')
}
</script>

<template>
  <div class="chat-header-container" :class="{ 'is-mobile': isMobile }">
    <div class="header-left">
      <div class="room-info" v-if="currentRoom">
        <Avatar :size="isMobile ? 36 : 40" :image="currentRoom.avatar" :text="currentRoom.chatName" :border-radius-ratio="0.3"
          class="room-avatar" />
        <div class="text-info">
          <div class="name-row">
            <h2 class="room-name">{{ currentRoom.chatName }}</h2>
            <span class="member-count">({{ currentRoom.memberCount }})</span>
          </div>
          <div class="room-status" v-if="!isMobile">
            <span class="id-label">{{ t('chat.room_id') }}</span>
            <span class="room-id">{{ currentRoom.chatCode }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="header-right">
      <div class="action-group">
        <!-- 移动端：成员列表入口按钮 -->
        <el-tooltip v-if="isMobile" :content="t('chat.members')" placement="bottom" :show-after="300">
          <div class="action-btn" @click="handleOpenMemberDrawer">
            <el-icon><User /></el-icon>
          </div>
        </el-tooltip>

        <el-tooltip :content="t('common.menu')" placement="bottom" :show-after="300">
          <el-dropdown trigger="click" placement="bottom-end" popper-class="ez-header-popper">
            <div class="action-btn"><el-icon>
                <MoreFilled />
              </el-icon></div>
            <template #dropdown>
              <el-dropdown-menu class="ez-dropdown-menu">
                <el-dropdown-item v-if="isOwner" @click="roomSettingsDialogVisible = true">
                  {{ t('chat.room_info') }}
                </el-dropdown-item>
                <el-dropdown-item v-if="canLeave" @click="confirmLeave">
                  {{ t('chat.leave_room') }}
                </el-dropdown-item>
                <el-dropdown-item v-if="canDisband" divided class="danger-item" @click="confirmDisband">
                  {{ t('chat.disband_room') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-tooltip>
      </div>
    </div>
  </div>

  <RoomSettingsDialog v-if="roomSettingsDialogVisible" />
</template>

<style scoped>
.chat-header-container {
  height: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light);
  /* 核心：使用变量适配分割线 */
  transition: all 0.3s ease;
}

/* 移动端 Header 紧凑布局 */
.chat-header-container.is-mobile {
  padding: 0 16px;
}

.header-left {
  display: flex;
  align-items: center;
}

.room-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.is-mobile .room-info {
  gap: 10px;
}

/* .room-avatar 样式由组件内部控制 */

.text-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.name-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.room-name {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-900);
  margin: 0;
}

.is-mobile .room-name {
  font-size: 15px;
}

.member-count {
  font-size: 13px;
  color: var(--text-500);
  font-weight: 600;
}

.is-mobile .member-count {
  font-size: 12px;
}

.room-status {
  display: flex;
  align-items: center;
  gap: 4px;
}

.id-label {
  font-size: 9px;
  font-weight: 800;
  color: var(--text-400);
  background: var(--bg-page);
  padding: 1px 4px;
  border-radius: 4px;
  letter-spacing: 0.5px;
  line-height: 1;
}

.room-id {
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace;
  font-size: 11px;
  color: var(--text-500);
  font-weight: 600;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.header-right {
  display: flex;
  align-items: center;
}

.action-group {
  display: flex;
  gap: 8px;
}

.is-mobile .action-group {
  gap: 4px;
}

.action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: var(--text-500);
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background-color: var(--bg-page);
  color: var(--primary);
}

.action-btn .el-icon {
  font-size: 20px;
}
</style>
