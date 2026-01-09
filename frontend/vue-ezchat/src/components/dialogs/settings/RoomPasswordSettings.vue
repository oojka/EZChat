<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PasswordConfig from '@/components/PasswordConfig.vue'
import { useRoomPasswordSettings } from '@/composables/useRoomPasswordSettings'

const { t } = useI18n()

const {
  passwordForm,
  passwordFormRef,
  passwordFormRules,
  isSaving,
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
      <div class="section-actions">
        <el-button type="primary" class="create-invite-btn" :loading="isSaving" @click="savePasswordSettings">
          {{ t('room_settings.password_save') }}
        </el-button>
      </div>
    </div>

    <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordFormRules" label-position="top"
      hide-required-asterisk>
      <PasswordConfig mode="always-visible" v-model="passwordForm.joinEnableByPassword"
        v-model:password="passwordForm.password" v-model:password-confirm="passwordForm.passwordConfirm"
        @enter="savePasswordSettings" />
    </el-form>
  </div>
</template>

<style scoped>
.step-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.create-invite-btn {
  border-radius: 8px;
  font-weight: 700;
}
</style>
