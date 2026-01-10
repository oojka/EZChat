<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled, Setting } from '@element-plus/icons-vue'
import Avatar from '@/components/Avatar.vue'
import type { Image } from '@/type'

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

const buildAvatarImage = (avatarUrl?: string): Image => ({
  imageName: '',
  imageUrl: avatarUrl || '',
  imageThumbUrl: avatarUrl || '',
  blobUrl: '',
  blobThumbUrl: '',
})

const avatarImage = computed(() => buildAvatarImage(props.avatar))

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'setting'): void
}>()
</script>

<template>
  <div class="user-item-wrapper" :class="{ 'is-offline': !isOnline, 'is-clickable': clickable }"
    @click="clickable && emit('click')">
    <div class="avatar-section">
      <Avatar class="user-avatar" :size="40" shape="square" :image="avatarImage" :text="nickname" />
    </div>
    <div class="info-section">
      <div class="name-row"><span class="nickname">{{ nickname }}</span></div>
      <div class="uid-row"><span class="id-label">UID</span><span class="uid-text">{{ uid }}</span></div>
    </div>
    <div class="action-section">
      <div class="setting-btn" @click.stop="emit('setting')">
        <el-icon :size="16">
          <Setting />
        </el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-item-wrapper {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-radius: var(--radius-base);
  user-select: none;
  background: transparent;
}

/* Removed hover background as requested */
/* .user-item-wrapper.is-clickable:hover {
  background-color: var(--bg-page);
} */

.is-offline {
  opacity: 0.7;
}

.avatar-section {
  margin-right: 14px;
  flex-shrink: 0;
}

.user-avatar {
  background-color: var(--bg-page);
  border: 2px solid var(--bg-card);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.nickname {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-900);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uid-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.id-label {
  font-size: 8px;
  font-weight: 800;
  color: var(--text-400);
  background: var(--bg-page);
  padding: 0px 3px;
  border-radius: var(--radius-ss);
  letter-spacing: 0.5px;
  line-height: 1.2;
  border: 1px solid var(--el-border-color-light);
}

.uid-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  color: var(--text-500);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.is-offline .user-avatar {
  filter: grayscale(0.5);
}

.is-offline .nickname {
  color: var(--text-500);
}

.action-section {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.setting-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-glass);
  border-radius: 8px;
  color: var(--text-500);
  cursor: pointer;
  transition: all 0.2s;
}

:global(html.dark) .setting-btn {
  background-color: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--text-400);
}

.setting-btn:hover {
  background-color: var(--primary);
  color: #fff;
  border-color: transparent;
}
</style>
