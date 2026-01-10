<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { Close, EditPen, Lock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/appStore'
import ProfileTab from './ProfileTab.vue'
import SecurityTab from './SecurityTab.vue'

const appStore = useAppStore()
const { userSettingsDialogVisible } = storeToRefs(appStore)
const { t } = useI18n()

// Tab State
type TabKey = 'profile' | 'security'
const activeTab = ref<TabKey>('profile')

const closeDialog = () => {
    userSettingsDialogVisible.value = false
}

// Reset state on open
watch(userSettingsDialogVisible, (val) => {
    if (val) {
        activeTab.value = 'profile'
    }
})

const handleSwitchTab = (tab: TabKey) => {
    if (activeTab.value === tab) return
    activeTab.value = tab
}

// Dynamic Title based on context
const contentTitle = computed(() => {
    if (activeTab.value === 'profile') return t('user_settings.profile_title')
    return t('user_settings.security_title')
})
</script>

<template>
    <el-dialog :model-value="userSettingsDialogVisible" @update:model-value="closeDialog" width="750px"
        class="ez-modern-dialog user-settings-dialog" align-center destroy-on-close :show-close="false"
        :close-on-click-modal="false" append-to-body>

        <div class="dialog-layout">
            <!-- Left Sidebar -->
            <div class="dialog-sidebar">
                <div class="sidebar-header">
                    <h3 class="dialog-title">
                        {{ t('user_settings.title') }}
                    </h3>
                </div>

                <div class="sidebar-menu">
                    <div class="menu-item" :class="{ active: activeTab === 'profile' }"
                        @click="handleSwitchTab('profile')">
                        <el-icon>
                            <EditPen />
                        </el-icon>
                        <span>{{ t('user_settings.profile_tab') }}</span>
                    </div>

                    <div class="menu-item" :class="{ active: activeTab === 'security' }"
                        @click="handleSwitchTab('security')">
                        <el-icon>
                            <Lock />
                        </el-icon>
                        <span>{{ t('user_settings.security_tab') }}</span>
                    </div>
                </div>
            </div>

            <!-- Right Content -->
            <div class="dialog-content">
                <!-- Content Header -->
                <div class="content-header">
                    <div class="header-left">
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
                        <!-- Profile Tab -->
                        <div v-if="activeTab === 'profile'" key="profile" class="tab-pane">
                            <ProfileTab @close="closeDialog" />
                        </div>

                        <!-- Security Tab -->
                        <div v-else key="security" class="tab-pane">
                            <SecurityTab @close="closeDialog" />
                        </div>
                    </Transition>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<style>
/* Global Dialog Overrides for this component */
.user-settings-dialog .el-dialog__header {
    display: none !important;
}

.user-settings-dialog .el-dialog__body {
    padding: 0 !important;
    margin: 0 !important;
    height: 600px;
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
    display: flex;
    flex-direction: column;
    padding: 24px 12px;
    flex-shrink: 0;
    border-radius: 8px;
    margin: 4px;
}

html.dark .dialog-sidebar {
    background: transparent;
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
}

html.dark .dialog-content {
    background: rgba(0, 0, 0, 0.2);
    border-radius: 8px;
    margin: 4px;
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

.content-body {
    flex: 1;
    overflow: hidden;
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
