<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowRight,
  Camera,
  CircleCheckFilled,
  CircleCloseFilled,
  Close,
  DocumentCopy,
  Picture,
} from '@element-plus/icons-vue'
import { useCreateChat } from '@/hooks/useCreateChat.ts'
import { storeToRefs } from 'pinia'
import { useRoomStore } from '@/stores/roomStore.ts'
import Avatar from '@/components/Avatar.vue'
import PasswordConfig from '@/components/PasswordConfig.vue'
import DateTimePicker from '@/components/DateTimePicker.vue'
import { useI18n } from 'vue-i18n'
import { useImageStore } from '@/stores/imageStore'

const roomStore = useRoomStore()
const { createChatDialogVisible } = storeToRefs(roomStore)

const { t } = useI18n()
const imageStore = useImageStore()

const {
  createChatForm,
  createStep,
  createResult,
  isCreating,
  handleCreate,
  createFormRef,
  createFormRules,
  beforeAvatarUpload,
  handleAvatarSuccess,
  selectedDate,
  selectedDateRadio,
  disabledDate,
  nextStep,
  prevStep,
  // 从组件下沉到 Hook 的逻辑
  tf,
  handleClose,
  roomIdDisplay,
  copyInviteLink,
  copyRoomId,
} = useCreateChat()

const defaultAvatarUrl = ref('') // 用于展示的默认头像 URL（不上传）

// 组件加载时生成默认头像 URL（仅用于展示）
onMounted(() => {
  defaultAvatarUrl.value = imageStore.generateDefaultAvatarUrl('room')
})

// 进度条百分比计算
const progressPercentage = computed(() => {
  if (createStep.value === 4 && createResult.value.success) {
    return 100
  }
  return (Math.min(createStep.value, 3) / 3) * 100
})

</script>

<template>
  <el-dialog :model-value="createChatDialogVisible" @update:model-value="handleClose" width="520px"
    class="ez-modern-dialog create-dialog-step" align-center destroy-on-close :show-close="false"
    :close-on-click-modal="false">
    <template #header>
      <div class="create-header">
        <div class="ez-dialog-header-actions">
          <button v-if="createStep !== 4" class="ez-close-btn" type="button" @pointerdown.stop.prevent
            @click="handleClose">
            <el-icon>
              <Close />
            </el-icon>
          </button>
        </div>
        <div class="progress-section">
          <el-progress :percentage="progressPercentage" :show-text="false" :stroke-width="4"
            :status="createStep === 4 ? (createResult.success ? 'success' : 'exception') : ''"
            class="custom-progress" />
          <div class="step-label">
            {{ createStep === 4 ? 'COMPLETED' : `Step ${createStep} / 3` }}
          </div>
        </div>
        <h4>
          {{ createStep === 4 ? t('create_chat.result') || '完成' : t('create_chat.title') }}
        </h4>
      </div>
    </template>

    <div class="create-dialog-container">

      <!-- 表单内容 -->
      <el-form ref="createFormRef" :model="createChatForm" :rules="createFormRules" class="create-form-content"
        label-position="top" hide-required-asterisk>
        <transition name="el-fade-in-linear" mode="out-in">
          <!-- Step 1: 头像上传 + 房间名称 -->
          <div v-if="createStep === 1" key="step1" class="step-container">
            <div class="form-vertical-stack">
              <!-- 头像上传 -->
              <div class="avatar-upload-box">
                <el-upload class="avatar-uploader-large" action="/api/auth/register/upload" :show-file-list="false"
                  :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload">
                  <!-- 统一使用 Avatar 组件 -->
                  <Avatar :thumb-url="createChatForm.avatar.imageThumbUrl || defaultAvatarUrl"
                    :url="createChatForm.avatar.imageUrl" :text="createChatForm.chatName" :size="150" shape="square"
                    editable :icon-size="40" />
                </el-upload>
                <div class="avatar-info-area">
                  <!-- 点击上传聊天室头像 -->
                  <p class="step-hint">{{ t('create_chat.avatar_upload_hint') || t('create_chat.avatar_hint') ||
                    t('auth.avatar_hint') }}</p>
                </div>
                <el-form-item prop="avatar" class="hidden-item" :show-message="false" />
              </div>

              <!-- 房间名称 -->
              <el-form-item :label="t('create_chat.room_name')" prop="chatName" class="spaced-item">
                <el-input v-model="createChatForm.chatName" :placeholder="t('create_chat.room_name_placeholder')"
                  size="large" @keydown.enter.prevent="nextStep" />
              </el-form-item>
            </div>
          </div>

          <!-- Step 2: 密码设置 -->
          <div v-else-if="createStep === 2" key="step2" class="step-container step-container-password">
            <PasswordConfig mode="always-visible" v-model="createChatForm.joinEnableByPassword"
              v-model:password="createChatForm.password" v-model:password-confirm="createChatForm.passwordConfirm"
              @enter="nextStep" />
          </div>

          <!-- Step 3: 过期时间设置 -->
          <div v-else-if="createStep === 3" key="step3" class="step-container">
            <DateTimePicker v-model="selectedDate" v-model:radio-value="selectedDateRadio"
              v-model:oneTimeLink="createChatForm.oneTimeLink" :disabled-date="disabledDate" />
          </div>

          <!-- Step 4: 结果反馈 -->
          <div v-else key="step4" class="step-container result-step">
            <div class="result-content">
              <!-- 顶部摘要：icon + 标题 + 消息（紧凑） -->
              <div class="result-summary">
                <el-icon :class="['result-icon', createResult.success ? 'success' : 'error']">
                  <CircleCheckFilled v-if="createResult.success" />
                  <CircleCloseFilled v-else />
                </el-icon>
                <h3 class="result-title">
                  {{ createResult.success ? (t('create_chat.success') || '创建成功') : (t('create_chat.fail') || '创建失败') }}
                </h3>
                <p class="result-message">{{ createResult.message }}</p>
              </div>

              <!-- 创建成功：展示 chatCode + 邀请链接（可滚动区域） -->
              <div v-if="createResult.success && createResult.chatCode" class="result-details">
                <div class="invite-block" style="margin-top: 30px;">
                  <!-- roomId：主视觉（大号数字） -->
                  <div class="credential-label">{{ tf('chat.room_id', '房间ID') }}</div>
                  <div class="credential-roomid-row">
                    <div class="credential-roomid-value">{{ roomIdDisplay }}</div>
                    <el-tooltip :content="tf('common.copy', '复制')" placement="top">
                      <el-button class="copy-icon-btn" :icon="DocumentCopy" @click="copyRoomId" />
                    </el-tooltip>
                  </div>

                  <!-- invite link：普通文本（不使用输入框） -->
                  <div class="credential-label" style="margin-top: 20px;">{{ tf('create_chat.invite_link', '邀请链接') }}
                  </div>
                  <div class="credential-link-row">
                    <div v-if="createResult.inviteUrl" class="credential-link-value">
                      {{ createResult.inviteUrl }}
                    </div>
                    <div v-else class="invite-empty">-</div>
                    <el-tooltip :content="tf('common.copy', '复制')" placement="top">
                      <el-button class="copy-icon-btn copy-icon-btn--primary" :icon="DocumentCopy"
                        @click="copyInviteLink" />
                    </el-tooltip>
                  </div>

                  <p class="invite-tip">
                    {{ tf('create_chat.invite_ttl_tip', '默认有效期 7 天；若关闭加入，邀请链接也将失效。') }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </transition>

        <!-- 操作按钮栏 -->
        <div class="create-actions">
          <template v-if="createStep === 1">
            <el-button type="primary" @click="nextStep" class="step-btn-full">
              {{ t('common.next') }}
              <el-icon class="el-icon--right">
                <ArrowRight />
              </el-icon>
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
            <el-button type="primary" @click="handleClose"
              :class="createResult.success ? 'step-btn-full' : 'step-btn-half'">
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
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

/* 重置 Element Plus header 默认样式，避免占位/边距影响自定义布局 */
.create-dialog-step :deep(.el-dialog__header) {
  /* 本弹窗使用 header slot，但要清零 EP 默认 16px padding（含 bottom） */
  padding: 0 !important;
  padding-bottom: 0 !important;
  margin: 0 !important;
}

.create-dialog-step :deep(.el-dialog__title) {
  display: none !important;
}

/* 同时清理 body 默认 padding：避免与 .create-dialog-container 的自定义 padding 叠加 */
.create-dialog-step :deep(.el-dialog__body) {
  padding: 0 !important;
}

.create-dialog-container {
  position: relative;
  /* header 与内容区域之间增加间距（padding-top），保持呼吸感 */
  padding: 10px 24px 16px;
  display: flex;
  flex-direction: column;
  /* 增加高度以容纳内容而不滚动 */
  min-height: 400px;
  overflow: visible;
}

/* --- Header --- */
.create-header {
  position: relative;
  text-align: center;
  margin-bottom: 8px;
  /* 回归统一视觉：顶部 20px，两侧 24px (标准 ez-dialog 边距) */
  padding: 20px 24px 0;
}

/* 覆盖全局位置：为了与顶部 Progress Bar (padding-top: 20px) 对齐 */
.create-header :deep(.ez-dialog-header-actions) {
  top: 6px;
  right: 12px;
}

.progress-section {
  margin-bottom: 8px;
  padding: 0 56px;
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
  margin: 12px 0 0;
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
  /* 增加高度，确保结果页内容能舒展显示 */
  height: 380px;
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
  margin-bottom: 24px;
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

.avatar-error-container {
  height: 20px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.avatar-error-text {
  font-size: 11px;
  color: var(--el-color-danger);
  font-weight: 600;
  text-align: center;
}

.avatar-uploader-large {
  text-align: center;
  display: flex;
  justify-content: center;
}

.avatar-preview-lg,
.placeholder-square-lg {
  width: 150px;
  height: 150px;
  border-radius: calc(150px * var(--avatar-border-radius-ratio));
  /* 45px (30%) */
  overflow: hidden;
  position: relative;
  cursor: pointer;
  margin: 0 auto;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  background: var(--bg-input);
  /* Recessed look */
}

.placeholder-square-lg {
  border: 2px dashed var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  gap: 8px;
}

.placeholder-square-lg span {
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
  /* 压缩 Step1 的纵向间距 */
  gap: 18px;
}

.spaced-item {
  /* 需求：ルーム名区域整体下移 10px（仅影响 Step1 的房间名表单项） */
  margin-top: 10px;
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

html.dark .config-glass-card {
  background: var(--bg-input);
  border-color: var(--el-border-color-darker);
  box-shadow: none;
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
  /* background-color: var(--bg-page) !important; -> Global in main.css */
  /* box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important; -> Global */
  border-radius: var(--radius-base);
  transition: all 0.3s;
  height: 48px !important;
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
  /* Step4 撑满容器高度，让 flex 子项可分配 */
  justify-content: flex-start;
  padding-top: 10px;
  /* 移除负边距，恢复正常布局 */
  margin-top: 0;
}

.result-content {
  display: flex;
  flex-direction: column;
  /* 使用 stretch 确保子元素宽度不超出父容器 */
  align-items: stretch;
  /* 恢复舒适间距 */
  gap: 16px;
  /* 撑满高度，让 result-details 可分配剩余空间 */
  height: 100%;
  min-height: 0;
}

/* 顶部摘要区：icon + 标题 + 消息（紧凑布局） */
.result-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

.result-icon {
  /* 基础尺寸（失败态） */
  font-size: 40px;
  transition: font-size 0.2s ease;
}

.result-icon.success {
  /* 恢复稍大的图标，保持视觉中心 */
  font-size: 64px;
  color: var(--el-color-success);
}

.result-icon.error {
  color: var(--el-color-danger);
}

.result-title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-900);
  line-height: 1.2;
}

.result-message {
  margin: 0;
  font-size: 14px;
  color: var(--text-500);
  line-height: 1.5;
  /* 单行省略，减少垂直噪音 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 详情区：可滚动，撑满剩余高度 */
.result-details {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  /* 美化滚动条 */
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color-light) transparent;
  padding-bottom: 2px;
}

.result-details::-webkit-scrollbar {
  width: 4px;
}

.result-details::-webkit-scrollbar-track {
  background: transparent;
}

.result-details::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: 2px;
}

.invite-block {
  width: 100%;
  /* 恢复标准内边距 */
  padding: 20px;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  display: flex;
  flex-direction: column;
  /* 恢复标准内部间距 */
  gap: 16px;
}

html.dark .invite-block {
  background: transparent;
  border-color: var(--el-border-color);
  backdrop-filter: none;
}

/* 成功页字段标题（ルームID / 招待リンク） */
.credential-label {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-400);
  letter-spacing: 0.5px;
  /* 首个 label 无需额外 margin-top */
  text-align: left;
  text-transform: uppercase;
}

/* roomId：大号数字 + 复制（数字视觉居中，按钮固定右侧） */
.credential-roomid-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  box-sizing: border-box;
  /* 恢复垂直边距 */
  margin: 4px 0 8px;
}

/* 左侧占位：与右侧复制按钮等宽，使数字视觉居中 */
.credential-roomid-row::before {
  content: '';
  width: 40px;
  flex-shrink: 0;
}

.credential-roomid-value {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace;
  /* 恢复字体大小 */
  font-size: clamp(36px, 6vw, 44px);
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--text-900);
  line-height: 1;
  user-select: text;
}

/* 邀请链接：普通文本展示 + 复制 */
.credential-link-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  box-sizing: border-box;
}

.credential-link-value {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  box-sizing: border-box;
  border-radius: 10px;
  background: var(--bg-input);
  /* Match global input style */
  border: 1px solid var(--el-border-color-light);
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-700);
  line-height: 1.4;
  word-break: break-all;
  overflow-wrap: anywhere;
  user-select: text;
  /* 限制最大高度，超长时滚动 */
  max-height: 64px;
  overflow-y: auto;
}

.invite-empty {
  flex: 1;
  min-width: 0;
  min-height: 40px;
  box-sizing: border-box;
  border-radius: 10px;
  background: var(--bg-input);
  /* Match global input style */
  border: 1px dashed var(--el-border-color-light);
  color: var(--text-400);
  display: flex;
  align-items: center;
  padding: 10px 12px;
}

.invite-copy-btn {
  height: 32px;
  padding: 0 12px;
  border-radius: 10px;
  font-weight: 800;
  flex-shrink: 0;
}

.invite-copy-primary {
  height: 44px;
  border-radius: 12px;
  padding: 0 14px;
}

/* 复制按钮：不使用蓝色 primary，改为 icon-only 的玻璃按钮（更"高级"且不抢主色） */
.copy-icon-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 12px;
  flex-shrink: 0;
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  color: var(--text-700);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  box-shadow: var(--shadow-glass);
  transition: transform 0.2s var(--ease-out-expo), background-color 0.2s var(--ease-out-expo);
  font-size: 16px;
}

.copy-icon-btn:hover {
  transform: translateY(-2px);
  background: var(--bg-page);
  color: var(--primary);
  border-color: var(--primary-light);
}

.copy-icon-btn:active {
  transform: translateY(0);
}

/* 更强调的复制按钮（邀请链接）：仍然不用蓝色，但通过边框/阴影做“主操作”层级 */
.copy-icon-btn--primary {
  border-color: rgba(64, 158, 255, 0.35);
}

.invite-tip {
  margin: 0;
  font-size: 11px;
  color: var(--text-500);
  line-height: 1.4;
  text-align: left;
  padding-top: 4px;
}

/* --- Header --- */
.create-header {
  position: relative;
  text-align: center;
  margin-bottom: 8px;
  /* 回归统一视觉：顶部 20px，两侧 24px (标准 ez-dialog 边距) */
  padding: 20px 24px 0;
}

/* 覆盖全局位置：为了与顶部 Progress Bar (padding-top: 20px) 对齐
   Progress Center Y = 20px + 2px = 22px
   Button Center Y needs 22px -> Top = 22px - 16px = 6px
   Right = 12px (slightly closer to edge for compact look) */
.create-header :deep(.ez-dialog-header-actions) {
  top: 6px;
  right: 12px;
}

.progress-section {
  margin-bottom: 8px;
  /* 增加水平内边距至 56px：
     CloseBtn Right(12px) + Width(32px) + Gap(12px) = 56px
     两侧对称缩短，避免进度条与右侧按钮重叠，并保持视觉居中 */
  padding: 0 56px;
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
  /* 进度条与标题间距：层级更清晰 */
  margin: 12px 0 0;
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
  /* 压缩 Step 区域高度（整体 dialog 高度也同步缩短） */
  height: 320px;
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
  margin-bottom: 24px;
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

.avatar-error-container {
  height: 20px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.avatar-error-text {
  font-size: 11px;
  color: var(--el-color-danger);
  font-weight: 600;
  text-align: center;
}

.avatar-uploader-large {
  text-align: center;
  display: flex;
  justify-content: center;
}

.avatar-preview-lg,
.placeholder-square-lg {
  width: 150px;
  height: 150px;
  border-radius: calc(150px * var(--avatar-border-radius-ratio));
  /* 45px (30%) */
  overflow: hidden;
  position: relative;
  cursor: pointer;
  margin: 0 auto;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  background: var(--bg-page);
}

.placeholder-square-lg {
  border: 2px dashed var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  gap: 8px;
}

.placeholder-square-lg span {
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
  /* 压缩 Step1 的纵向间距 */
  gap: 18px;
}

.spaced-item {
  /* 需求：ルーム名区域整体下移 10px（仅影响 Step1 的房间名表单项） */
  margin-top: 10px;
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
  /* background-color: var(--bg-page) !important; -> Global in main.css */
  /* box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important; -> Global */
  border-radius: var(--radius-base);
  transition: all 0.3s;
  height: 48px !important;
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
  /* Step4 撑满容器高度，让 flex 子项可分配 */
  justify-content: flex-start;
  padding-top: 0;
  /* 整体上移，留出底部空间 */
  margin-top: -15px;
}

.result-content {
  display: flex;
  flex-direction: column;
  /* 使用 stretch 确保子元素宽度不超出父容器 */
  align-items: stretch;
  /* 极度紧凑 */
  gap: 4px;
  /* 撑满高度，让 result-details 可分配剩余空间 */
  height: 100%;
  min-height: 0;
}

/* 顶部摘要区：icon + 标题 + 消息（紧凑布局） */
.result-summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

.result-icon {
  /* 基础尺寸（失败态） */
  font-size: 40px;
  transition: font-size 0.2s ease;
}

.result-icon.success {
  /* 成功态放大，但不要太大 */
  font-size: 48px;
  color: var(--el-color-success);
}

.result-icon.error {
  color: var(--el-color-danger);
}

.result-title {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--text-900);
  line-height: 1.2;
}

.result-message {
  margin: 0;
  font-size: 12px;
  color: var(--text-500);
  line-height: 1.3;
  /* 单行省略，减少垂直噪音 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

/* 详情区：可滚动，撑满剩余高度 */
.result-details {
  flex: 1;
  min-height: 0;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
  /* 美化滚动条 */
  scrollbar-width: thin;
  scrollbar-color: var(--el-border-color-light) transparent;
  padding-bottom: 0;
}

.result-details::-webkit-scrollbar {
  width: 4px;
}

.result-details::-webkit-scrollbar-track {
  background: transparent;
}

.result-details::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: 2px;
}

.invite-block {
  width: 100%;
  /* 进一步缩小内边距 */
  padding: 10px 14px;
  box-sizing: border-box;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  display: flex;
  flex-direction: column;
  /* 紧凑间距 */
  gap: 8px;
}

/* 成功页字段标题（ルームID / 招待リンク） */
.credential-label {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-400);
  letter-spacing: 0.5px;
  /* 首个 label 无需额外 margin-top */
  margin-top: 0;
  text-align: left;
  text-transform: uppercase;
}

/* roomId：大号数字 + 复制（数字视觉居中，按钮固定右侧） */
.credential-roomid-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  box-sizing: border-box;
  /* 极小垂直边距 */
  margin: 2px 0 4px;
}

/* 左侧占位：与右侧复制按钮等宽，使数字视觉居中 */
.credential-roomid-row::before {
  content: '';
  width: 32px;
  flex-shrink: 0;
}

.credential-roomid-value {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace;
  /* 进一步缩小 Room ID 字体 */
  font-size: clamp(28px, 5vw, 36px);
  font-weight: 800;
  letter-spacing: 0.08em;
  color: var(--text-900);
  line-height: 1;
  user-select: text;
}

/* 邀请链接：普通文本展示 + 复制 */
.credential-link-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  box-sizing: border-box;
}

.credential-link-value {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  box-sizing: border-box;
  border-radius: 6px;
  background: var(--bg-page);
  border: 1px solid var(--el-border-color-light);
  font-family: 'JetBrains Mono', 'Fira Code', 'Roboto Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-700);
  line-height: 1.3;
  word-break: break-all;
  overflow-wrap: anywhere;
  user-select: text;
  /* 限制最大高度 */
  max-height: 48px;
  overflow-y: auto;
}

.invite-empty {
  flex: 1;
  min-width: 0;
  min-height: 32px;
  box-sizing: border-box;
  border-radius: 6px;
  background: var(--bg-page);
  border: 1px dashed var(--el-border-color-light);
  color: var(--text-400);
  display: flex;
  align-items: center;
  padding: 6px 10px;
}

.invite-copy-btn {
  height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  font-weight: 800;
  flex-shrink: 0;
}

.invite-copy-primary {
  height: 36px;
  border-radius: 8px;
  padding: 0 10px;
}

/* 复制按钮：不使用蓝色 primary，改为 icon-only 的玻璃按钮（更"高级"且不抢主色） */
.copy-icon-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 8px;
  flex-shrink: 0;
  background: var(--bg-glass);
  border: 1px solid var(--border-glass);
  color: var(--text-700);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  box-shadow: var(--shadow-glass);
  transition: transform 0.2s var(--ease-out-expo), background-color 0.2s var(--ease-out-expo);
  font-size: 14px;
}

.copy-icon-btn:hover {
  transform: translateY(-2px);
  background: var(--bg-page);
  color: var(--primary);
  border-color: var(--primary-light);
}

.copy-icon-btn:active {
  transform: translateY(0);
}

/* 更强调的复制按钮（邀请链接）：仍然不用蓝色，但通过边框/阴影做“主操作”层级 */
.copy-icon-btn--primary {
  border-color: rgba(64, 158, 255, 0.35);
}

.invite-tip {
  margin: 0;
  font-size: 10px;
  color: var(--text-500);
  line-height: 1.2;
  text-align: left;
  padding-top: 2px;
}

@media (max-width: 520px) {
  .credential-roomid-value {
    font-size: clamp(28px, 8vw, 36px);
  }

  .credential-roomid-row {
    flex-direction: column;
    align-items: stretch;
  }

  /* 移动端隐藏左侧占位元素 */
  .credential-roomid-row::before {
    display: none;
  }

  .credential-link-row {
    flex-direction: column;
    align-items: stretch;
  }

  .copy-icon-btn {
    align-self: flex-end;
  }
}

/* --- Actions --- */
.create-actions {
  display: flex;
  gap: 12px;
  margin-top: auto;
  padding-top: 8px;
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
