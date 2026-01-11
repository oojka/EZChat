<script setup lang="ts">
import { ref } from 'vue'
import { useFriendStore } from '@/stores/friendStore'
import Avatar from '@/components/Avatar.vue'
import UserProfileDialog from '@/components/dialogs/UserProfileDialog.vue'
import { ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { MoreFilled } from '@element-plus/icons-vue'

const { t } = useI18n()
const friendStore = useFriendStore()

const profileVisible = ref(false)
const selectedUid = ref('')

const handleChat = (uid: string) => {
  friendStore.startChat(uid)
}

const handleRemove = (uid: string) => {
  ElMessageBox.confirm(
    t('friend.remove_confirm'),
    t('common.warning'),
    {
      confirmButtonText: t('friend.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    }
  ).then(() => {
    friendStore.removeFriend(uid)
  })
}

const handleShowProfile = (uid: string) => {
  selectedUid.value = uid
  profileVisible.value = true
}
</script>

<template>
  <div class="friend-list-container">
    <div v-if="friendStore.friends.length === 0" class="empty-state">
      <el-empty :description="t('friend.no_friends')" :image-size="60" />
    </div>
    
    <div v-else class="friend-list">
      <div 
        v-for="friend in friendStore.friends" 
        :key="friend.userId" 
        class="friend-item"
        :class="{ 'is-offline': !friend.online }"
        @click="handleChat(friend.uid)"
        @contextmenu.prevent="handleRemove(friend.uid)"
      >
        <div class="friend-info">
          <div class="avatar-wrapper">
            <Avatar 
              :image="friend.avatar" 
              :text="friend.nickname" 
              :size="44" 
            />
            <div class="status-dot" :class="{ online: friend.online }"></div>
          </div>
          
          <div class="details">
            <div class="top-row">
              <span class="nickname">{{ friend.alias || friend.nickname }}</span>
            </div>
            <div class="bio text-truncate">{{ friend.bio || t('friend.no_bio') }}</div>
          </div>
        </div>

        <div class="actions" @click.stop>
          <el-button 
            type="text" 
            class="more-btn" 
            @click="handleShowProfile(friend.uid)"
          >
            <el-icon :size="18"><MoreFilled /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <UserProfileDialog 
      v-model:visible="profileVisible" 
      :uid="selectedUid" 
    />
  </div>
</template>

<style scoped>
.friend-list-container {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.friend-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 2px;
}

.friend-item:hover {
  background-color: var(--bg-glass-overlay);
}

.friend-item:hover .more-btn {
  opacity: 1;
}

.is-offline {
  opacity: 0.6;
}

.is-offline .avatar-wrapper {
  filter: grayscale(1);
}

.friend-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.avatar-wrapper {
  position: relative;
  transition: filter 0.3s ease;
}

.status-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #9ca3af;
  border: 2px solid var(--bg-card);
}

.status-dot.online {
  background-color: #10b981;
}

.details {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nickname {
  font-weight: 500;
  color: var(--text-900);
  font-size: 15px;
}

.is-offline .nickname {
  color: var(--text-500);
}

.bio {
  font-size: 13px;
  color: var(--text-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-state {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}

.more-btn {
  color: var(--text-400);
  opacity: 0;
  transition: all 0.2s;
  padding: 8px;
}

.more-btn:hover {
  color: var(--primary);
  background-color: var(--bg-fill-1);
  border-radius: 8px;
}
</style>
