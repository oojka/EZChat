<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { Close, ArrowLeft, Connection, Lock, EditPen, UserFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRoomStore } from '@/stores/roomStore'
import { useRoomInviteManager } from '@/composables/room/useRoomInviteManager'
import RoomInviteList from './RoomInviteList.vue'
import RoomInviteCreate from './RoomInviteCreate.vue'
import RoomPasswordSettings from './RoomPasswordSettings.vue'
import RoomBasicSettings from './RoomBasicSettings.vue'
import RoomMemberManagement from './RoomMemberManagement.vue'

const roomStore = useRoomStore()
const { roomSettingsDialogVisible, currentRoom } = storeToRefs(roomStore)
const { t } = useI18n()

// Tab State
type TabKey = 'basic' | 'invites' | 'security' | 'members'
const activeTab = ref<TabKey>('basic')

// Invite Logic State
// 1: List, 2: Create (Only valid when activeTab === 'invites')
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

// Reset state on open
watch(roomSettingsDialogVisible, (val) => {
  if (val) {
    activeTab.value = 'basic'
    currentStep.value = 1
  }
})

const handleSwitchTab = (tab: TabKey) => {
  if (activeTab.value === tab) return
  activeTab.value = tab
  // Reset create flow when switching tabs if needed
  if (tab === 'invites') {
    currentStep.value = 1
  }
}

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

// Dynamic Title based on context
const contentTitle = computed(() => {
  if (activeTab.value === 'basic') return t('room_settings.basic_title')
  if (activeTab.value === 'security') return t('room_settings.room_password')
  if (activeTab.value === 'members') return t('room_settings.member_title')
  // activeTab === 'invites'
  if (currentStep.value === 2) {
    return t('room_settings.create_invite_title')
  }
  return t('chat.room_info') // Or "Invite Management"
})
</script>

<template>
  <el-dialog :model-value="roomSettingsDialogVisible" @update:model-value="closeDialog" width="900px"
    class="ez-modern-dialog room-settings-dialog" align-center destroy-on-close :show-close="false"
    :close-on-click-modal="false" append-to-body>

    <div class="dialog-layout">
      <!-- Left Sidebar -->
      <div class="dialog-sidebar">
        <div class="sidebar-header">
          <h3 class="dialog-title">
            {{ t('chat.room_info') }}
          </h3>
          <p class="room-name" v-if="currentRoom?.chatName">{{ currentRoom.chatName }}</p>
        </div>

        <div class="sidebar-menu">
          <div class="menu-item" :class="{ active: activeTab === 'basic' }" @click="handleSwitchTab('basic')">
            <el-icon>
              <EditPen />
            </el-icon>
            <span>{{ t('room_settings.basic_tab') }}</span>
          </div>


          <div class="menu-item" :class="{ active: activeTab === 'members' }" @click="handleSwitchTab('members')">
            <el-icon>
              <UserFilled />
            </el-icon>
            <span>{{ t('room_settings.members_tab') }}</span>
          </div>


          <div class="menu-item" :class="{ active: activeTab === 'invites' }" @click="handleSwitchTab('invites')">
            <el-icon>
              <Connection />
            </el-icon>
            <span>{{ t('room_settings.invites_tab') }}</span>
          </div>

          <div class="menu-item" :class="{ active: activeTab === 'security' }" @click="handleSwitchTab('security')">
            <el-icon>
              <Lock />
            </el-icon>
            <span>{{ t('room_settings.security_tab') }}</span>
          </div>


        </div>
      </div>

      <!-- Right Content -->
      <div class="dialog-content">
        <!-- Content Header -->
        <div class="content-header">
          <div class="header-left">
            <button v-if="activeTab === 'invites' && currentStep === 2" class="back-icon-btn" @click="handleBackToList">
              <el-icon>
                <ArrowLeft />
              </el-icon>
            </button>
            <h4>{{ contentTitle }}</h4>
          </div>

          <button class="ez-close-btn" type="button" @click="closeDialog">
            <el-icon>
              <Close />
            </el-icon>
          </button>
        </div>

        <!-- Content Body -->
        <div class="content-body">
          <Transition name="fade-slide" mode="out-in">
            <!-- Basic Tab -->
            <div v-if="activeTab === 'basic'" key="basic" class="tab-pane">
              <RoomBasicSettings />
            </div>

            <!-- Members Tab -->
            <div v-else-if="activeTab === 'members'" key="members" class="tab-pane">
              <RoomMemberManagement />
            </div>

            <!-- Security Tab -->
            <div v-else-if="activeTab === 'security'" key="security" class="tab-pane">
              <RoomPasswordSettings />
            </div>

            <!-- Invites Tab -->
            <div v-else key="invites" class="tab-pane">
              <Transition name="fade-slide" mode="out-in">
                <!-- List View -->
                <RoomInviteList v-if="currentStep === 1" :invite-list="inviteList" :invite-limit-tip="inviteLimitTip"
                  :is-loading="isLoading" :revoking-id="revokingId" :can-create="canCreate" @create="handleGoToCreate"
                  @revoke="confirmRevoke" @copy="copyInviteUrl" />

                <!-- Create View -->
                <RoomInviteCreate v-else :is-creating="isCreating" :disabled-date="disabledDate" v-model="selectedDate"
                  v-model:radio-value="selectedDateRadio" v-model:one-time-link="oneTimeLink" @confirm="handleDoCreate"
                  @cancel="handleBackToList" />
              </Transition>
            </div>


          </Transition>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style>
/* Global Dialog Overrides for this component */
.room-settings-dialog .el-dialog__header {
  display: none !important;
  /* Hide default header completely */
}

.room-settings-dialog .el-dialog__body {
  padding: 0 !important;
  margin: 0 !important;
  height: 650px;
  /* Fixed height requested by user */
  display: flex;
}
</style>

<style scoped>
.dialog-layout {
  display: flex;
  width: 100%;
  height: 100%;
}

/* Sidebar */
.dialog-sidebar {
  width: 200px;
  background: var(--bg-page);
  /* Light Gray in Light Mode */
  display: flex;
  flex-direction: column;
  padding: 24px 12px;
  flex-shrink: 0;
  border-radius: 8px;
  /* Round all corners */
  margin: 4px;
  /* Add margin for floating effect */
}

html.dark .dialog-sidebar {
  background: transparent;
  /* Make sidebar lighter (transparent) in Dark Mode */
  border-right: none;
}

.sidebar-header {
  padding: 0 12px 24px;
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-900);
}

.room-name {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-500);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-500);
  transition: all 0.2s ease;
  font-size: 14px;
  font-weight: 500;
}

.menu-item:hover {
  background: var(--el-fill-color-light);
  color: var(--text-700);
}

.menu-item.active {
  background: var(--primary-light);
  color: var(--primary);
}

html.dark .menu-item.active {
  background: color-mix(in srgb, var(--primary), transparent 85%);
  color: var(--primary);
}

/* Right Content */
.dialog-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-card);
  /* White in Light Mode */
  /* Prevent flex overflow */
}

html.dark .dialog-content {
  background: rgba(0, 0, 0, 0.2);
  /* Dark overlay for content area */
  border-radius: 8px;
  /* Rounded corners on all sides for 'card' effect */
  margin: 4px;
  /* Add slight margin to separate from sidebar/edges */
}

.content-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--border-light);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-900);
}

.back-icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-500);
  transition: background 0.2s;
}

.back-icon-btn:hover {
  background: var(--bg-active);
  color: var(--text-900);
}

.content-body {
  flex: 1;
  overflow: hidden;
  /* Disable generic scroll, let tabs manage it */
  padding: 24px;
  position: relative;
  display: flex;
  flex-direction: column;
}

.tab-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  /* Crucial for nested flex scrolling */
}

/* Animations */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
</style>
