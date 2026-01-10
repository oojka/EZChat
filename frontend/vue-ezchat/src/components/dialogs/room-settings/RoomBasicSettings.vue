<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Avatar from '@/components/Avatar.vue'
import { useRoomBasicSettings } from '@/composables/room/useRoomBasicSettings'

const { t } = useI18n()

const {
  form,
  formRef,
  formRules,
  isSaving,
  displayAvatar,
  beforeAvatarUpload,
  handleAvatarSuccess,
  saveBasicSettings,
} = useRoomBasicSettings()
</script>

<template>
  <div class="basic-settings">
    <div class="section-head">
      <div>
        <h4>{{ t('room_settings.basic_section_title') }}</h4>
        <p class="section-subtitle">{{ t('room_settings.basic_section_desc') }}</p>
      </div>
      <div class="section-actions">
        <el-button type="primary" class="save-btn" :loading="isSaving" @click="saveBasicSettings">
          {{ t('room_settings.basic_save') }}
        </el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" hide-required-asterisk>
      <div class="basic-grid">
        <div class="avatar-block">
          <el-upload class="avatar-uploader" action="/api/auth/register/upload" :show-file-list="false"
            :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload">
            <Avatar :image="displayAvatar" :text="form.chatName" :size="140" shape="square" editable :icon-size="36" />
          </el-upload>
          <p class="avatar-tip">{{ t('room_settings.basic_avatar_tip') }}</p>
        </div>

        <div class="fields-block">
          <el-form-item prop="chatName" :label="t('room_settings.basic_chat_name')">
            <el-input v-model="form.chatName" :placeholder="t('room_settings.basic_chat_name_placeholder')" />
          </el-form-item>

          <el-form-item prop="maxMembers" :label="t('room_settings.basic_max_members')">
            <div class="max-members-row">
              <el-input-number v-model="form.maxMembers" :min="2" :max="200" controls-position="right" />
              <span class="field-hint">{{ t('room_settings.basic_max_members_hint') }}</span>
            </div>
          </el-form-item>

          <el-form-item prop="announcement" :label="t('room_settings.basic_announcement')">
            <el-input v-model="form.announcement" type="textarea" :rows="5" maxlength="500" show-word-limit
              :placeholder="t('room_settings.basic_announcement_placeholder')" />
          </el-form-item>
        </div>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.basic-settings {
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

.save-btn {
  border-radius: 8px;
  font-weight: 700;
}

.basic-grid {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 24px;
}

.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.avatar-uploader {
  display: flex;
  justify-content: center;
}

.avatar-tip {
  margin: 0;
  font-size: 12px;
  color: var(--text-400);
  text-align: center;
}

.fields-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.max-members-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-hint {
  font-size: 12px;
  color: var(--text-400);
}

@media (max-width: 768px) {
  .basic-grid {
    grid-template-columns: 1fr;
  }

  .avatar-block {
    align-items: flex-start;
  }
}
</style>
