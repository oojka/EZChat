<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  ArrowRight,
  Camera,
  CircleCheckFilled,
  CircleCloseFilled,
  Close,
  Picture,
} from '@element-plus/icons-vue'
import { useCreateChat } from '@/hooks/useCreateChat.ts'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/appStore.ts'
import PasswordConfig from '@/components/PasswordConfig.vue'
import DateTimePicker from '@/components/DateTimePicker.vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const appStore = useAppStore()
const { createRoomVisible } = storeToRefs(appStore)

const { t } = useI18n()

const {
  createChatForm,
  createStep,
  createResult,
  isCreating,
  hasPasswordError,
  passwordErrorMessage,
  handleCreate,
  createFormRef,
  createFormRules,
  beforeAvatarUpload,
  handleAvatarSuccess,
  selectedDate,
  selectedDateRadio,
  disabledDate,
  resetCreateForm,
  nextStep,
  prevStep,
} = useCreateChat()

// 进度条百分比计算
const progressPercentage = computed(() => {
  if (createStep.value === 4 && createResult.value.success) {
    return 100
  }
  return (Math.min(createStep.value, 3) / 3) * 100
})

// 监听弹窗关闭，自动重置表单
watch(createRoomVisible, (newVal) => {
  if (!newVal) {
    // 弹窗关闭时重置
    setTimeout(() => {
      resetCreateForm()
    }, 300) // 延迟一点，等关闭动画完成
  }
})

const handleClose = () => {
  createRoomVisible.value = false
}

const handleAvatarUploadSuccess = (response: any, uploadFile: any, fileList: any) => {
  handleAvatarSuccess(response, uploadFile, fileList)
}
</script>

<template>
  <el-dialog
    v-model="createRoomVisible"
    width="580px"
    class="ez-modern-dialog create-dialog-step"
    align-center
    destroy-on-close
    :show-close="false"
    :close-on-click-modal="false"
  >
    <div class="create-dialog-container">
      <!-- 关闭按钮 -->
      <el-button v-if="createStep !== 4" class="close-btn" :icon="Close" circle @click="handleClose" />
      
      <!-- 头部：进度条和标题 -->
      <div class="create-header">
          <div class="progress-section">
          <el-progress
            :percentage="progressPercentage"
            :show-text="false"
            :stroke-width="4"
            :status="createStep === 4 ? (createResult.success ? 'success' : 'exception') : ''"
            class="custom-progress"
          />
          <div class="step-label">
            {{ createStep === 4 ? 'COMPLETED' : `Step ${createStep} / 3` }}
          </div>
        </div>
        <h4>
          {{ createStep === 4 ? t('create_chat.result') || '完成' : t('create_chat.title') }}
        </h4>
      </div>

      <!-- 表单内容 -->
      <el-form
        ref="createFormRef"
        :model="createChatForm"
        :rules="createFormRules"
        class="create-form-content"
        label-position="top"
        hide-required-asterisk
      >
        <transition name="el-fade-in-linear" mode="out-in">
          <!-- Step 1: 头像上传 + 房间名称 -->
          <div v-if="createStep === 1" key="step1" class="step-container">
            <div class="form-vertical-stack">
              <!-- 头像上传 -->
              <div class="avatar-upload-box">
                <el-upload
                  class="avatar-uploader-large"
                  action="/api/auth/register/upload"
                  :show-file-list="false"
                  :on-success="handleAvatarUploadSuccess"
                  :before-upload="beforeAvatarUpload"
                >
                  <div v-if="createChatForm.avatar.objectThumbUrl" class="avatar-preview-lg">
                    <img :src="createChatForm.avatar.objectThumbUrl" class="avatar-img" />
                    <div class="edit-mask-lg">
                      <el-icon><Camera /></el-icon>
                      <span>{{ t('common.change') }}</span>
                    </div>
                  </div>
                  <div v-else class="placeholder-circle-lg">
                    <el-icon size="40"><Picture /></el-icon>
                    <span>{{ t('create_chat.avatar_label') || t('auth.select_image') }}</span>
                  </div>
                </el-upload>
                <div class="avatar-info-area">
                  <p class="step-hint">{{ t('create_chat.avatar_hint') || t('auth.avatar_hint') }}</p>
                </div>
                <el-form-item prop="avatar" class="hidden-item" :show-message="false" />
              </div>

              <!-- 房间名称 -->
              <el-form-item :label="t('create_chat.room_name')" prop="chatName" class="spaced-item">
                <el-input
                  v-model="createChatForm.chatName"
                  :placeholder="t('create_chat.room_name_placeholder')"
                  size="large"
                  @keydown.enter.prevent="nextStep"
                />
              </el-form-item>
            </div>
          </div>

          <!-- Step 2: 密码设置 -->
          <div v-else-if="createStep === 2" key="step2" class="step-container step-container-password">
            <PasswordConfig
              v-model="createChatForm.joinEnable"
              v-model:password="createChatForm.password"
              v-model:password-confirm="createChatForm.passwordConfirm"
              :has-password-error="hasPasswordError"
              :password-error-message="passwordErrorMessage"
              @enter="nextStep"
            />
          </div>

          <!-- Step 3: 过期时间设置 -->
          <div v-else-if="createStep === 3" key="step3" class="step-container">
            <div class="form-vertical-stack">
              <div class="config-glass-card">
                <DateTimePicker
                  v-model="selectedDate"
                  v-model:radio-value="selectedDateRadio"
                  :disabled-date="disabledDate"
                />
              </div>
            </div>
          </div>

          <!-- Step 4: 结果反馈 -->
          <div v-else key="step4" class="step-container result-step">
            <div class="result-content">
              <el-icon :class="['result-icon', createResult.success ? 'success' : 'error']">
                <CircleCheckFilled v-if="createResult.success" />
                <CircleCloseFilled v-else />
              </el-icon>
              <h3 class="result-title">
                {{ createResult.success ? (t('create_chat.success') || '创建成功') : (t('create_chat.fail') || '创建失败') }}
              </h3>
              <p class="result-message">{{ createResult.message }}</p>
            </div>
          </div>
        </transition>

        <!-- 操作按钮栏 -->
        <div class="create-actions">
          <template v-if="createStep === 1">
            <el-button type="primary" @click="nextStep" class="step-btn-full">
              {{ t('common.next') }}
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </template>
          <template v-else-if="createStep === 2">
            <el-button @click="prevStep" class="step-btn-half">{{ t('common.back') }}</el-button>
            <el-button type="primary" @click="nextStep" class="step-btn-half">
              {{ t('common.next') }}
            </el-button>
          </template>
          <template v-else-if="createStep === 3">
            <el-button @click="prevStep" class="step-btn-half" :disabled="isCreating">
              {{ t('common.back') }}
            </el-button>
            <el-button type="primary" @click="handleCreate" class="step-btn-half" :loading="isCreating">
              {{ t('create_chat.submit') }}
            </el-button>
          </template>
          <template v-else>
            <el-button v-if="!createResult.success" @click="createStep = 1" class="step-btn-half">
              {{ t('common.retry') }}
            </el-button>
            <el-button
              type="primary"
              @click="handleClose"
              :class="createResult.success ? 'step-btn-full' : 'step-btn-half'"
            >
              {{ t('create_chat.enter_now') || t('common.confirm') }}
            </el-button>
          </template>
        </div>
      </el-form>
    </div>
  </el-dialog>
</template>

<style scoped>
/* --- Dialog Container --- */
:deep(.ez-modern-dialog) {
  background: var(--bg-glass) !important;
  backdrop-filter: var(--blur-glass) !important;
  -webkit-backdrop-filter: var(--blur-glass) !important;
  border: 1px solid var(--border-glass) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-glass) !important;
  overflow: hidden;
  transition: all 0.3s var(--ease-out-expo);
}

html.dark :deep(.ez-modern-dialog) {
  background: var(--bg-card) !important;
  backdrop-filter: blur(24px) saturate(200%) !important;
  -webkit-backdrop-filter: blur(24px) saturate(200%) !important;
}

.create-dialog-container {
  position: relative;
  padding: 24px 32px 24px;
  display: flex;
  flex-direction: column;
  min-height: 500px;
}

.close-btn {
  position: absolute;
  right: -16px;
  top: -12px;
  z-index: 10;
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.close-btn:hover {
  background: var(--el-border-color-light);
  color: var(--text-900);
  transform: rotate(90deg);
}

/* --- Header --- */
.create-header {
  text-align: center;
  margin-bottom: 16px;
}

.progress-section {
  margin-bottom: 12px;
  padding: 0 40px;
}

:deep(.custom-progress .el-progress-bar__outer) {
  background-color: var(--el-border-color-extra-light) !important;
}

.step-label {
  font-size: 10px;
  font-weight: 800;
  color: var(--primary);
  letter-spacing: 1px;
}

.create-header h4 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-900);
}

/* --- Form Content --- */
.create-form-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.step-container {
  height: 400px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.step-container-password {
  justify-content: flex-start;
  padding-top: 0;
}

/* --- Avatar Upload Box --- */
.avatar-upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 32px;
}

.avatar-info-area {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.step-hint {
  text-align: center;
  font-size: 11px;
  color: var(--text-400);
  margin: 0;
}

.avatar-uploader-large {
  text-align: center;
  display: flex;
  justify-content: center;
}

.avatar-preview-lg,
.placeholder-circle-lg {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  margin: 0 auto;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  background: var(--bg-page);
}

.placeholder-circle-lg {
  border: 2px dashed var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  gap: 8px;
}

.placeholder-circle-lg span {
  font-size: 12px;
  font-weight: 700;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-mask-lg {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: 0.3s;
  font-size: 12px;
  gap: 4px;
}

.avatar-preview-lg:hover .edit-mask-lg {
  opacity: 1;
}

.hidden-item {
  margin: 0 !important;
  height: 0;
  overflow: hidden;
}

/* --- Form Vertical Stack --- */
.form-vertical-stack {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 24px;
}

.spaced-item {
  margin-bottom: 0 !important;
}

:deep(.el-form-item) {
  margin-bottom: 12px;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-700);
  padding-bottom: 4px !important;
  line-height: 1 !important;
}

/* --- Config Cards --- */
.config-glass-card {
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  border-radius: var(--radius-md);
  padding: 24px;
  box-shadow: var(--shadow-glass);
  transition: all 0.3s var(--ease-out-expo);
}

.config-glass-card:hover {
  border-color: var(--primary-light);
  transform: translateY(-2px);
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title-with-icon {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 800;
  color: var(--text-700);
  padding-left: 4px;
}

/* --- Input Styles --- */
:deep(.el-input__wrapper) {
  background-color: var(--bg-page) !important;
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  border-radius: var(--radius-base);
  transition: all 0.3s;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

:deep(.el-input__inner) {
  color: var(--text-900) !important;
}

/* --- Result Step --- */
.result-step {
  text-align: center;
}

.result-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.result-icon {
  font-size: 64px;
}

.result-icon.success {
  color: var(--el-color-success);
}

.result-icon.error {
  color: var(--el-color-danger);
}

.result-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--text-900);
}

.result-message {
  margin: 0;
  font-size: 14px;
  color: var(--text-500);
  line-height: 1.6;
}

/* --- Actions --- */
.create-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 12px;
}

.step-btn-full {
  width: 100%;
  height: 44px;
  border-radius: var(--radius-base);
  font-weight: 800;
  font-size: 14px;
}

.step-btn-half {
  flex: 1;
  height: 44px;
  border-radius: var(--radius-base);
  font-weight: 800;
  font-size: 14px;
}

.el-button--default.step-btn-half {
  background: var(--bg-page);
  border: none;
  color: var(--text-500);
  transition: all 0.3s;
}

.el-button--default.step-btn-half:hover {
  background: var(--el-border-color-light);
  color: var(--text-700);
}

</style>
