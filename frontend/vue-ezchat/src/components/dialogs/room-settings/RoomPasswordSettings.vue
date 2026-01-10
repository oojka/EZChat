<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PasswordInput from '@/components/PasswordInput.vue'
import { useRoomPasswordSettings } from '@/composables/room/useRoomPasswordSettings'

const { t } = useI18n()

const {
  passwordForm,
  passwordFormRef,
  passwordFormRules,
  isSaving,
  isEditing,
  handleToggle,
  openEditor,
  cancelEdit,
  savePasswordSettings,
} = useRoomPasswordSettings()
</script>

<template>
  <div class="step-content">
    <div class="section-head">
      <div>
        <h4>{{ t('room_settings.password_section_title') }}</h4>
        <p class="section-subtitle">{{ t('room_settings.password_section_desc') }}</p>
      </div>
    </div>

    <div class="password-panel">
      <div class="toggle-row">
        <div class="toggle-meta">
          <span class="toggle-title">{{ t('room_settings.room_password') }}</span>
        </div>
        <el-switch v-model="passwordForm.joinEnableByPassword" :active-value="1" :inactive-value="0" :loading="isSaving"
          @change="handleToggle" />
      </div>

      <div v-if="passwordForm.joinEnableByPassword === 1" class="change-row">
        <el-button type="primary" :disabled="isEditing" class="change-btn" @click="openEditor">
          {{ t('room_settings.password_change') }}
        </el-button>
      </div>

      <div v-if="isEditing" class="edit-panel">
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordFormRules" label-position="top"
          hide-required-asterisk>
          <div class="password-inputs-grid">
            <el-form-item prop="password" class="no-label-item show-error-inline">
              <PasswordInput v-model="passwordForm.password" :placeholder="t('auth.password')"
                @enter="savePasswordSettings" />
            </el-form-item>
            <el-form-item prop="passwordConfirm" class="no-label-item show-error-inline">
              <PasswordInput v-model="passwordForm.passwordConfirm"
                :placeholder="t('auth.confirm_password_placeholder')" @enter="savePasswordSettings" />
            </el-form-item>
          </div>
        </el-form>
        <div class="edit-actions">
          <el-button class="cancel-btn" @click="cancelEdit">
            {{ t('common.cancel') }}
          </el-button>
          <el-button type="primary" class="save-btn" :loading="isSaving" @click="savePasswordSettings">
            {{ t('room_settings.password_save') }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.step-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  overflow-y: auto;
}

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

.password-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-glass);
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toggle-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-700);
}

.change-row {
  display: flex;
  justify-content: flex-start;
}

.edit-panel {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.password-inputs-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.show-error-inline :deep(.el-form-item__error) {
  font-size: 11px;
  font-weight: 600;
  padding-top: 4px;
}

.no-label-item :deep(.el-form-item__label) {
  display: none !important;
}

.edit-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.save-btn,
.cancel-btn,
.change-btn {
  border-radius: 8px;
  font-weight: 700;
}
</style>
