<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Plus, Ticket, DocumentCopy, Delete } from '@element-plus/icons-vue'
import type { ChatInvite } from '@/type'

const { t } = useI18n()

interface Props {
    inviteList: ChatInvite[]
    inviteLimitTip: string
    isLoading: boolean
    revokingId: number | null
    canCreate: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
    (e: 'create'): void
    (e: 'revoke', id: number): void
    (e: 'copy', code: string): void
}>()

const formatUsage = (invite: ChatInvite) => {
    if (invite.maxUses === 0) {
        return `${invite.usedCount}/${t('room_settings.unlimited')}`
    }
    return `${invite.usedCount}/${invite.maxUses}`
}

const formatDateTime = (dateStr: string) => {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    return date.toLocaleString()
}
</script>

<template>
    <div class="step-content">
        <div class="section-head">
            <div>
                <h4>{{ t('room_settings.invite_section_title') }}</h4>
                <p class="section-subtitle">{{ t('room_settings.invite_section_desc') }}</p>
            </div>
            <div class="section-actions">
                <el-button type="primary" class="create-invite-btn" :disabled="!props.canCreate"
                    @click="$emit('create')">
                    <el-icon class="el-icon--left">
                        <Plus />
                    </el-icon>
                    {{ t('room_settings.new') }}
                </el-button>
            </div>
        </div>

        <!-- Limit Tip -->
        <div class="limit-bar">
            <span class="limit-text">{{ props.inviteLimitTip }}</span>
            <el-progress :percentage="(props.inviteList.length / 5) * 100" :show-text="false" :stroke-width="4"
                :status="props.inviteList.length >= 5 ? 'exception' : ''" class="limit-progress" />
        </div>

        <div class="invite-list-container">
            <el-skeleton v-if="props.isLoading" animated :rows="3" />
            <el-empty v-else-if="props.inviteList.length === 0" :description="t('room_settings.empty_invites')"
                :image-size="60" />
            <div v-else class="invite-items-wrapper">
                <div v-for="invite in props.inviteList" :key="invite.id" class="invite-card">
                    <div class="card-icon">
                        <el-icon>
                            <Ticket />
                        </el-icon>
                    </div>
                    <div class="invite-info">
                        <div class="invite-code">{{ invite.inviteCode }}</div>
                        <div class="invite-meta">
                            <span>{{ formatDateTime(invite.expiresAt) }}</span>
                            <span class="divider">|</span>
                            <span>{{ formatUsage(invite) }}</span>
                        </div>
                    </div>
                    <div class="invite-actions">
                        <el-tooltip :content="t('common.copy')" placement="top">
                            <button class="action-mini-btn" @click="$emit('copy', invite.inviteCode)">
                                <el-icon>
                                    <DocumentCopy />
                                </el-icon>
                            </button>
                        </el-tooltip>
                        <el-tooltip :content="t('room_settings.revoke')" placement="top">
                            <button class="action-mini-btn danger" :disabled="props.revokingId === invite.id"
                                @click="$emit('revoke', invite.id)">
                                <el-icon :class="{ 'is-loading': props.revokingId === invite.id }">
                                    <Delete />
                                </el-icon>
                            </button>
                        </el-tooltip>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.step-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

/* --- Section Head --- */
.section-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding-bottom: 8px;
    border-bottom: 1px dashed var(--el-border-color-lighter);
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

/* --- Limit Bar --- */
.limit-bar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 4px;
}

.limit-text {
    font-size: 11px;
    color: var(--text-500);
    white-space: nowrap;
}

.limit-progress {
    width: 120px;
}

/* --- Invite List --- */
.invite-list-container {
    flex: 1;
    overflow-y: auto;
    /* max-height: 360px; REMOVED */
    padding-right: 4px;
    /* scrollbar space */
}

/* Custom Scrollbar for list */
.invite-list-container::-webkit-scrollbar {
    width: 4px;
}

.invite-list-container::-webkit-scrollbar-thumb {
    background: var(--el-border-color);
    border-radius: 4px;
}

.invite-items-wrapper {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.invite-card {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 14px;
    border-radius: 12px;
    background: var(--bg-card);
    border: 1px solid var(--el-border-color-light);
    transition: all 0.2s ease;
}

.invite-card:hover {
    border-color: var(--primary-light);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.card-icon {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    background: var(--el-fill-color-light);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-400);
}

.invite-info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.invite-code {
    font-size: 15px;
    font-weight: 800;
    color: var(--text-900);
    font-family: 'JetBrains Mono', monospace;
    letter-spacing: 0.5px;
}

.invite-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    color: var(--text-500);
}

.invite-meta .divider {
    color: var(--el-border-color);
    font-size: 10px;
}

/* --- Mini Actions --- */
.invite-actions {
    display: flex;
    align-items: center;
    gap: 6px;
}

.action-mini-btn {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
    border: none;
    background: transparent;
    color: var(--text-500);
    cursor: pointer;
    transition: all 0.2s;
}

.action-mini-btn:hover {
    background: var(--el-fill-color);
    color: var(--primary);
}

.action-mini-btn.danger:hover {
    background: var(--el-color-danger-light-9);
    color: var(--el-color-danger);
}

.create-invite-btn {
    border-radius: 8px;
    font-weight: 700;
}
</style>
