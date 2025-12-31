<script setup lang="ts">
import {
  Camera,
  InfoFilled,
  Link,
  Lock,
  Plus,
  User,
} from '@element-plus/icons-vue'
import { useCreateChat } from '@/hooks/useCreateChat.ts'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/appStore.ts'
import PasswordInput from '@/components/PasswordInput.vue'
import DateTimePicker from '@/components/DateTimePicker.vue'
import { useI18n } from 'vue-i18n'

const appStore = useAppStore()
const { createRoomVisible } = storeToRefs(appStore)

const { t } = useI18n()

const {
  createChatForm,
  handleCreate,
  createFormRef,
  createFormRules,
  beforeAvatarUpload,
  handleAvatarSuccess,
  selectedDate,
  selectedDateRadio,
  disabledDate,
} = useCreateChat()



</script>

<template>
  <el-dialog
    v-model="createRoomVisible"
    width="820px"
    class="modern-dialog create-dialog-wide"
    align-center
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="false"
  >
    <template #header="{ close }">
      <div class="dialog-banner create-banner">
        <div class="banner-content">
          <h3>{{ t('create_chat.title') }}</h3>
          <p>{{ t('create_chat.subtitle') }}</p>
        </div>
        <el-icon class="banner-icon"><Plus /></el-icon>
        <div class="close-btn" @click="close">×</div>
      </div>
    </template>

    <div class="dialog-body-content no-padding">
      <el-form
        ref="createFormRef"
        :model="createChatForm"
        :rules="createFormRules"
        class="create-form-horizontal"
        label-position="top"
        hide-required-asterisk
      >
        <div class="form-left-panel">
          <div class="avatar-section">
            <el-upload
              class="avatar-uploader"
              action="/api/auth/register/upload"
              :show-file-list="false"
              :on-success="handleAvatarSuccess"
              :before-upload="beforeAvatarUpload"
            >
              <div v-if="createChatForm.avatar.objectThumbUrl" class="avatar-preview-lg">
                <img :src="createChatForm.avatar.objectThumbUrl" class="avatar-img" />
                <div class="edit-mask-lg">
                  <el-icon><Camera /></el-icon><span>{{ t('create_chat.change_avatar') }}</span>
                </div>
              </div>
              <div v-else class="placeholder-circle-lg">
                <el-icon size="28"><Camera /></el-icon><span>{{ t('create_chat.avatar_label') }}</span>
              </div>
            </el-upload>
            <div class="left-panel-hint">{{ t('create_chat.avatar_hint') }}</div>
          </div>

          <div class="room-rules-block">
            <div class="rules-header">
              <el-icon><InfoFilled /></el-icon><span>{{ t('create_chat.guide_title') }}</span>
            </div>
            <div class="rules-list">
              <div class="rule-item">
                <el-icon class="rule-icon"><Lock /></el-icon>
                <div class="rule-content">
                  <span class="rule-title">{{ t('create_chat.restriction_title') }}</span
                  ><span class="rule-desc">{{ t('create_chat.restriction_desc') }}</span>
                </div>
              </div>
              <div class="rule-item">
                <el-icon class="rule-icon"><Link /></el-icon>
                <div class="rule-content">
                  <span class="rule-title">{{ t('create_chat.invite_url_title') }}</span
                  ><span class="rule-desc">{{ t('create_chat.invite_url_desc') }}</span>
                </div>
              </div>
              <div class="rule-item">
                <el-icon class="rule-icon"><User /></el-icon>
                <div class="rule-content">
                  <span class="rule-title">{{ t('create_chat.capacity_title') }}</span
                  ><span class="rule-desc">{{ t('create_chat.capacity_desc') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="form-right-panel">
          <div class="section-group">
            <el-form-item :label="t('create_chat.room_name')" prop="chatName" class="mb-0">
              <el-input
                v-model="createChatForm.chatName"
                :placeholder="t('create_chat.room_name_placeholder')"
                class="custom-input name-input-lg"
              />
            </el-form-item>
          </div>

          <div class="config-cards-stack">
            <div class="config-card-flat security-card">
              <div class="card-title-row space-between">
                <div class="flex-center gap-2">
                  <el-icon><Lock /></el-icon><span>{{ t('create_chat.password_join') }}</span>
                </div>
                <el-switch
                  v-model="createChatForm.joinEnable"
                  :active-value="1"
                  :inactive-value="0"
                  :active-text="t('common.on')"
                  :inactive-text="t('common.off')"
                  inline-prompt
                />
              </div>
              <transition name="el-zoom-in-top">
                <div v-if="createChatForm.joinEnable === 1" class="password-fields">
                  <div class="password-grid">
                    <el-form-item :label="t('auth.password')" prop="password" class="mb-0">
                      <PasswordInput
                        v-model="createChatForm.password"
                        :placeholder="t('auth.password')"
                        size="default"
                      />
                    </el-form-item>
                    <el-form-item :label="t('auth.confirm_password')" prop="passwordConfirm" class="mb-0">
                      <PasswordInput
                        v-model="createChatForm.passwordConfirm"
                        :placeholder="t('auth.confirm_password_placeholder')"
                        size="default"
                      />
                    </el-form-item>
                  </div>
                </div>
              </transition>
            </div>

            <DateTimePicker
              v-model="selectedDate"
              v-model:radio-value="selectedDateRadio"
              :disabled-date="disabledDate"
            />
          </div>
        </div>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer footer-wide">
        <el-button @click="createRoomVisible = false" class="btn-cancel-wide">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate" class="btn-submit create-btn-final"
          >{{ t('create_chat.submit') }}</el-button
        >
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.modern-dialog) {
  border-radius: var(--radius-xl);
  overflow: hidden;
  box-shadow: var(--shadow-glass);
  border: 1px solid var(--border-glass);
  background: var(--bg-card);
}
.dialog-banner {
  height: 70px;
  padding: 0 32px;
  color: #fff;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
}
.banner-content h3 {
  font-size: 20px;
  font-weight: 900;
  margin: 0;
}
.banner-icon {
  position: absolute;
  right: -10px;
  bottom: -20px;
  font-size: 100px;
  opacity: 0.12;
  transform: rotate(-12deg);
}
.close-btn {
  position: absolute;
  right: 20px;
  top: 20px;
  font-size: 20px;
  cursor: pointer;
  opacity: 0.6;
}

.create-form-horizontal {
  display: flex;
  background: var(--bg-card);
  min-height: 360px;
}
.form-left-panel {
  width: 240px;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: var(--bg-aside);
  border-right: 1px solid var(--el-border-color-light);
}
.form-right-panel {
  flex: 1;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background-color: var(--bg-card);
}

.avatar-preview-lg,
.placeholder-circle-lg {
  width: 100px;
  height: 100px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  position: relative;
  background: var(--bg-page);
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.edit-mask-lg {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 30px;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  gap: 4px;
}
.placeholder-circle-lg {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-500);
  background-color: var(--bg-page);
  border: 2px dashed var(--el-border-color);
  gap: 8px;
}
.left-panel-hint {
  font-size: 10px;
  color: var(--text-500);
  margin-top: 12px;
}

.room-rules-block {
  width: 100%;
  background: var(--bg-page);
  border-radius: var(--radius-md);
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  margin-top: 24px;
}
.rules-header {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-700);
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.rule-icon {
  font-size: 14px;
  color: var(--primary);
  margin-top: 2px;
}
.rule-content {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.rule-title {
  font-size: 10px;
  font-weight: 700;
  color: var(--text-900);
}
.rule-desc {
  font-size: 9px;
  color: var(--text-500);
}

.section-group {
  margin-bottom: 8px;
}
.config-cards-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.config-card-flat {
  border-radius: var(--radius-md);
  padding: 14px 18px;
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
}
.security-card {
  background-color: var(--bg-page);
}
.card-title-row {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-700);
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.card-title-row.space-between {
  justify-content: space-between;
  margin-bottom: 0;
}
.flex-center {
  display: flex;
  align-items: center;
}
.gap-2 {
  gap: 8px;
}

.password-fields {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}
.password-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

:deep(.el-form-item__label) {
  font-weight: 800;
  color: var(--text-700);
  font-size: 11px;
  line-height: 1.5;
  margin-bottom: 6px !important;
}
:deep(.el-input__wrapper) {
  height: 44px !important;
  border-radius: var(--radius-base) !important;
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  background-color: var(--bg-page) !important;
}
:deep(.el-input__inner) {
  color: var(--text-900);
}
:deep(.name-input-lg .el-input__inner) {
  font-weight: 900;
  font-size: 15px;
}

.footer-wide {
  padding: 12px 32px 32px;
  display: flex;
  justify-content: flex-end;
  background: var(--bg-card);
}
.btn-cancel-wide {
  height: 44px;
  padding: 0 24px;
  border-radius: var(--radius-base);
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  font-weight: 700;
  margin-right: 12px;
}
.create-btn-final {
  height: 44px;
  padding: 0 32px;
  border-radius: var(--radius-base);
  font-weight: 800;
  font-size: 14px;
  background: var(--primary);
  border: none;
  color: #fff;
  box-shadow: 0 8px 16px rgba(64, 158, 255, 0.2);
}

.mb-0 {
  margin-bottom: 0 !important;
}
.avatar-uploader :deep(.el-upload) {
  border: none;
  background: transparent;
  cursor: pointer;
  position: relative;
  overflow: visible;
}
</style>
