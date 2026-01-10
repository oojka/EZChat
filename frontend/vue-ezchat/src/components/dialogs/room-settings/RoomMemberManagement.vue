<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Avatar from '@/components/Avatar.vue'
import { useRoomMemberManagement } from '@/composables/room/useRoomMemberManagement'

const { t } = useI18n()

const {
  members,
  ownerUid,
  selectedUids,
  isKicking,
  isTransferring,
  kickSelected,
  kickMember,
  transferOwner,
} = useRoomMemberManagement()
</script>

<template>
  <div class="member-management">
    <div class="section-head">
      <div>
        <h4>{{ t('room_settings.member_section_title') }}</h4>
        <p class="section-subtitle">{{ t('room_settings.member_section_desc') }}</p>
      </div>
      <div class="section-actions">
        <el-button type="danger" class="kick-btn" :loading="isKicking" :disabled="selectedUids.length === 0"
          @click="kickSelected">
          {{ t('room_settings.member_kick') }}
        </el-button>
      </div>
    </div>

    <div class="member-list">
      <el-scrollbar style="height: 100%">
        <el-empty v-if="members.length === 0" :description="t('room_settings.member_empty')" />
        <el-checkbox-group v-else v-model="selectedUids" class="member-checkbox-group">
          <div v-for="member in members" :key="member.uid" class="member-row">
            <el-checkbox :label="member.uid" :disabled="member.uid === ownerUid">
              <div class="member-content">
                <div class="member-info">
                  <Avatar :image="member.avatar" :size="40" shape="square" />
                  <div class="member-meta">
                    <div class="name-row">
                      <span class="name">{{ member.nickname || member.uid }}</span>
                      <span v-if="member.uid === ownerUid" class="owner-tag">
                        {{ t('room_settings.member_owner_tag') }}
                      </span>
                    </div>
                    <div class="uid-row">
                      <span class="id-label">UID</span>
                      <span class="uid-text">{{ member.uid }}</span>
                    </div>
                  </div>
                </div>
                <div class="member-actions" @click.stop>
                  <el-button size="small" type="primary" plain :loading="isTransferring"
                    :disabled="member.uid === ownerUid" @click.stop="transferOwner(member.uid)">
                    {{ t('room_settings.member_transfer_action') }}
                  </el-button>
                  <el-button size="small" type="danger" plain :loading="isKicking" :disabled="member.uid === ownerUid"
                    @click.stop="kickMember(member.uid)">
                    {{ t('room_settings.member_kick') }}
                  </el-button>
                </div>
              </div>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
.member-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
  /* Important for flex child scroll */
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  flex-shrink: 0;
  /* Keep header fixed */
}

.section-head h4 {
  margin: 0 0 2px;
  font-size: 15px;
  font-weight: 800;
  color: var(--text-800);
}

.section-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--text-400);
}

.kick-btn {
  border-radius: 8px;
  font-weight: 700;
}

/* Removed .member-list container styles for cleaner look */
.member-list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.member-checkbox-group {
  display: flex;
  flex-direction: column;
}

.member-row {
  padding: 8px 12px;
  /* Adjusted padding for larger avatar */
  border-radius: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  transition: background-color 0.2s;
  display: flex;
  align-items: center;
}

.member-row:nth-child(even) {
  background-color: var(--el-fill-color-extra-light);
}

/* Force Checkbox to full width */
.member-row :deep(.el-checkbox) {
  width: 100%;
  margin-right: 0;
  height: auto;
}

.member-row :deep(.el-checkbox__label) {
  flex: 1;
  width: 0;
  /* prevent overflow */
}

.member-row:last-child {
  border-bottom: none;
}

.member-row:hover {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  /* Rounded on hover */
}

.member-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.member-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.member-meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-900);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.owner-tag {
  font-size: 9px;
  padding: 1px 6px;
  background: var(--primary);
  color: #fff;
  border-radius: 100px;
  font-weight: 900;
  letter-spacing: 0.5px;
  flex-shrink: 0;
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.2);
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
  border-radius: 4px;
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

.member-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
