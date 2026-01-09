<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { Close, ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { useRoomInviteManager } from '@/composables/useRoomInviteManager'
import RoomInviteList from './settings/RoomInviteList.vue'
import RoomInviteCreate from './settings/RoomInviteCreate.vue'
import RoomPasswordSettings from './settings/RoomPasswordSettings.vue'

const roomStore = useRoomStore()
const { roomSettingsDialogVisible, currentRoom } = storeToRefs(roomStore)
const { t } = useI18n()

// 1: 列表页, 2: 创建页
const currentStep = ref<1 | 2>(1)

const {
  inviteList,
  inviteLimitTip,
  isLoading,
  isCreating,
  revokingId,
  selectedDate,
  selectedDateRadio,
  oneTimeLink,
  disabledDate,
  canCreate,
  createInvite,
  confirmRevoke,
  copyInviteUrl,
  resetForm,
} = useRoomInviteManager()

const closeDialog = () => {
  roomSettingsDialogVisible.value = false
}

// 监听弹窗打开，重置为第一页
watch(roomSettingsDialogVisible, (val) => {
  if (val) {
    currentStep.value = 1
  }
})

const handleGoToCreate = () => {
  resetForm()
  currentStep.value = 2
}

const handleBackToList = () => {
  currentStep.value = 1
}

const handleDoCreate = async () => {
  const success = await createInvite()
  if (success) {
    currentStep.value = 1
  }
}

const stepTitle = computed(() => {
  if (currentStep.value === 1) return t('chat.room_info')
  return t('room_settings.create_invite_title') || 'New Invite Link'
})
</script>

<template>
  <el-dialog :model-value="roomSettingsDialogVisible" @update:model-value="closeDialog" width="520px"
    class="ez-modern-dialog room-settings-dialog" align-center destroy-on-close :show-close="false"
    :close-on-click-modal="false" append-to-body>
    <template #header>
      <div class="settings-header">
        <div class="header-actions">
          <!-- Step 2: Show back button -->
          <button v-if="currentStep === 2" class="ez-close-btn back-btn" type="button" @click="handleBackToList">
            <el-icon>
              <ArrowLeft />
            </el-icon>
          </button>

          <button v-else class="ez-close-btn" type="button" @click="closeDialog">
            <el-icon>
              <Close />
            </el-icon>
          </button>
        </div>
        <div class="title-area">
          <h3>{{ stepTitle }}</h3>
          <p class="room-subtitle" v-if="currentRoom?.chatName && currentStep === 1">
            {{ currentRoom.chatName }}
          </p>
        </div>
      </div>
    </template>

    <div class="settings-body">
      <Transition name="el-fade-in-linear" mode="out-in">

        <!-- Step 1: Invite List -->
        <div v-if="currentStep === 1" key="list" class="settings-stack">
          <RoomInviteList :invite-list="inviteList" :invite-limit-tip="inviteLimitTip" :is-loading="isLoading"
            :revoking-id="revokingId" :can-create="canCreate" @create="handleGoToCreate" @revoke="confirmRevoke"
            @copy="copyInviteUrl" />
          <RoomPasswordSettings />
        </div>

        <!-- Step 2: Create Form -->
        <RoomInviteCreate v-else key="create" :is-creating="isCreating" :disabled-date="disabledDate"
          v-model="selectedDate" v-model:radio-value="selectedDateRadio" v-model:one-time-link="oneTimeLink"
          @confirm="handleDoCreate" @cancel="handleBackToList" />
      </Transition>
    </div>
  </el-dialog>
</template>

<style>
.ez-modern-dialog {
  background: var(--bg-glass) !important;
  backdrop-filter: var(--blur-glass) !important;
  -webkit-backdrop-filter: var(--blur-glass) !important;
  border: 1px solid var(--border-glass) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-glass) !important;
  overflow: hidden;
  transition: all 0.3s var(--ease-out-expo);
}

html.dark .ez-modern-dialog {
  background: var(--bg-card) !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

.room-settings-dialog .el-dialog__header {
  padding: 0 !important;
  margin: 0 !important;
}
</style>

<style scoped>
.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 24px 12px;
}

.header-actions {
  display: flex;
  align-items: center;
}

.title-area {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-area h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-900);
}

.room-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--text-500);
}

.settings-body {
  padding: 12px 24px 32px;
  display: flex;
  flex-direction: column;
  min-height: 480px;
}

.settings-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
