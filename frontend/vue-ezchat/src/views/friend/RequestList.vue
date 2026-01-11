<script setup lang="ts">
import { useFriendStore } from '@/stores/friendStore'
import { Check, Close } from '@element-plus/icons-vue'
import Avatar from '@/components/Avatar.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const friendStore = useFriendStore()

const handleAccept = (reqId: number) => {
  friendStore.handleRequest(reqId, true)
}

const handleReject = (reqId: number) => {
  friendStore.handleRequest(reqId, false)
}
</script>

<template>
  <div v-if="friendStore.requests.length > 0" class="request-list">
    <div class="section-title">{{ t('friend.new_requests') }}</div>
    <div 
      v-for="req in friendStore.requests" 
      :key="req.id" 
      class="request-item glass-panel"
    >
      <div class="info">
        <Avatar 
          :image="req.senderAvatar" 
          :text="req.senderNickname" 
          :size="40" 
          class="avatar"
        />
        <div class="text">
          <div class="name">{{ req.senderNickname }}</div>
          <div class="uid">{{ t('friend.uid', [req.senderUid]) }}</div>
        </div>
      </div>
      <div class="actions">
        <el-button circle size="small" type="success" :icon="Check" @click="handleAccept(req.id)" />
        <el-button circle size="small" type="danger" :icon="Close" @click="handleReject(req.id)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.request-list {
  padding: 0 16px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 12px;
  color: var(--text-400);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}

.request-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
  margin-bottom: 8px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid var(--border-glass);
}

.info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.text {
  display: flex;
  flex-direction: column;
}

.name {
  font-weight: 600;
  color: var(--text-900);
  font-size: 14px;
}

.uid {
  font-size: 12px;
  color: var(--text-500);
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
