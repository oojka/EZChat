<script setup lang="ts">
  import { ref } from 'vue'
  import { storeToRefs } from 'pinia'
  import {
    ArrowRight,
    Link,
    Lock,
    Close,
    CircleCheckFilled,
    CircleCloseFilled,
  } from '@element-plus/icons-vue'
  import { useJoinChat } from '@/hooks/useJoinChat'
  import { useRoomStore } from '@/stores/roomStore'
  import { useI18n } from 'vue-i18n'
  
  const { t } = useI18n()
  const roomStore = useRoomStore()
  const { joinChatDialogVisible } = storeToRefs(roomStore)
  
  // 使用 Hook 提供的业务逻辑
  const {
    joinChatCredentialsForm,
    joinChatCredentialsFormRules,
    isLoading,
    isRoomIdPasswordMode,
    isInviteUrlMode,
    joinMode,
    joinStep,
    joinResult,
    closeJoinDialog,
    handleJoin,
    handleResultConfirm,
  } = useJoinChat()

  

  </script>
  
  <template>
    <el-dialog
      :model-value="joinChatDialogVisible"
      @update:model-value="closeJoinDialog"
      width="480px"
      class="ez-modern-dialog join-dialog-modern"
      align-center 
      destroy-on-close
      :show-close="false"
      :close-on-click-modal="false"
    >
      <template #header>
        <div class="dialog-header-actions">
          <button v-if="joinStep === 1" class="close-btn" type="button" @click="closeJoinDialog">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div v-if="joinStep === 1" class="dialog-title-area">
          <h3>{{ t('chat.join_chat') }}</h3>
        </div>
      </template>
  
      <div class="join-dialog-content">
        <transition name="el-fade-in-linear" mode="out-in">
          
          <div v-if="joinStep === 1" key="step1" class="step-container">
            
            <div class="mode-toggle-pill">
              <div 
                :class="['mode-tab', { active: isRoomIdPasswordMode }]"
                @click="joinMode = 'roomId/password'"
              >
                <el-icon><Lock /></el-icon>
                <span>{{ t('chat.password_mode') }}</span>
              </div>
              <div 
                :class="['mode-tab', { active: !isRoomIdPasswordMode }]"
                @click="joinMode = 'inviteUrl'"
              >
                <el-icon><Link /></el-icon>
                <span>{{ t('chat.invite_mode') }}</span>
              </div>
            </div>
  
            <el-form
              :model="joinChatCredentialsForm"
              :rules="joinChatCredentialsFormRules"
              ref="joinFormRef"
              label-position="top"
              class="join-form"
              hide-required-asterisk
              @submit.prevent="handleJoin"
            >
              <template v-if="isRoomIdPasswordMode">
                <el-form-item :label="t('chat.chat_code')" prop="chatCode">
                  <el-input
                    v-model="joinChatCredentialsForm.chatCode"
                    :placeholder="t('chat.chat_code_placeholder')"
                    size="large"
                    maxlength="8"
                    @keydown.enter.prevent="handleJoin"
                  />
                </el-form-item>
  
                <el-form-item :label="t('chat.password')" prop="password">
                  <el-input
                    v-model="joinChatCredentialsForm.password"
                    :placeholder="t('chat.password_placeholder')"
                    type="password"
                    size="large"
                    show-password
                    @keydown.enter.prevent="handleJoin"
                  />
                </el-form-item>
              </template>
  
              <template v-else>
                <el-form-item :label="t('chat.invite_code')" prop="inviteCode">
                  <el-input
                    v-model="joinChatCredentialsForm.inviteCode"
                    :placeholder="t('chat.invite_code_placeholder')"
                    size="large"
                    maxlength="24"
                    @keydown.enter.prevent="handleJoin"
                  />
                </el-form-item>
                
                <div class="invite-hint-box">
                  <el-icon><Link /></el-icon>
                  <p>{{ t('chat.invite_code_hint') || '请输入邀请链接末尾的字符代码' }}</p>
                </div>
              </template>
            </el-form>
  
            <div class="dialog-actions">
              <el-button 
                type="primary" 
                class="action-btn-full" 
                size="large" 
                :loading="isLoading"
                @click="handleJoin"
              >
                {{ t('chat.join') }}
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
  
          <div v-else key="step2" class="step-container result-container">
            <div class="result-content">
              <el-icon :class="['result-icon', joinResult.success ? 'success' : 'error']">
                <CircleCheckFilled v-if="joinResult.success" />
                <CircleCloseFilled v-else />
              </el-icon>
              
              <h3 class="result-title">
                {{ joinResult.success ? (t('chat.join_success') || '加入成功') : (t('chat.join_failed') || '加入失败') }}
              </h3>
              
              <p class="result-message">{{ joinResult.message }}</p>
            </div>
  
            <div class="dialog-actions">
              <el-button 
                :type="joinResult.success ? 'primary' : 'default'"
                class="action-btn-full" 
                size="large"
                @click="handleResultConfirm"
              >
                {{ joinResult.success ? (t('create_chat.enter_now') || '立即进入') : (t('common.retry') || '重试') }}
              </el-button>
            </div>
          </div>
  
        </transition>
      </div>
    </el-dialog>
  </template>
  
  <style scoped>
  /* --- 基础弹窗样式 (继承全局 Glassmorphism 变量) --- */
  :deep(.ez-modern-dialog) {
    background: var(--bg-glass) !important;
    backdrop-filter: var(--blur-glass) !important;
    -webkit-backdrop-filter: var(--blur-glass) !important;
    border: 1px solid var(--border-glass) !important;
    border-radius: var(--radius-xl) !important;
    box-shadow: var(--shadow-glass) !important;
    overflow: hidden;
  }
  
  html.dark :deep(.ez-modern-dialog) {
    background: var(--bg-card) !important;
  }
  
  /* 重置 Element Plus Header */
  .join-dialog-modern :deep(.el-dialog__header) {
    padding: 0 !important;
    margin: 0 !important;
  }
  .join-dialog-modern :deep(.el-dialog__body) {
    padding: 0 !important;
  }
  
  /* 内容布局 */
  .join-dialog-content {
    padding: 10px 32px 32px;
    position: relative;
    min-height: 360px; /* 固定最小高度，防止切换时弹窗跳动 */
    display: flex;
    flex-direction: column;
  }
  
  /* --- 头部区域 --- */
  .dialog-header-actions {
    position: relative;
    height: 20px;
  }
  
  .close-btn {
    position: absolute;
    right: 16px;
    top: 16px;
    z-index: 10;
    background: var(--bg-page);
    border: none;
    color: var(--text-500);
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.3s;
  }
  
  .close-btn:hover {
    background: var(--el-border-color-light);
    color: var(--text-900);
    transform: rotate(90deg);
  }
  
  .dialog-title-area {
    text-align: center;
    margin-top: 12px;
    margin-bottom: 24px;
  }
  
  .dialog-title-area h3 {
    font-size: 20px;
    font-weight: 800;
    color: var(--text-900);
    margin: 0;
  }
  
  /* --- 步骤容器 --- */
  .step-container {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
  
  /* --- 模式切换 Tabs --- */
  .mode-toggle-pill {
    display: flex;
    background: var(--bg-page);
    padding: 4px;
    border-radius: 14px;
    margin-bottom: 24px;
    border: 1px solid var(--el-border-color-light);
  }
  
  .mode-tab {
    flex: 1;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 13px;
    font-weight: 700;
    color: var(--text-500);
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.3s var(--ease-out-expo);
  }
  
  .mode-tab:hover {
    color: var(--text-700);
  }
  
  .mode-tab.active {
    background: var(--bg-card);
    color: var(--primary);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  }
  
  html.dark .mode-tab.active {
    background: var(--el-bg-color-overlay);
  }
  
  /* --- 表单样式 --- */
  .join-form {
    flex: 1;
  }
  
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
  
  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 700;
    color: var(--text-700);
    padding-bottom: 6px !important;
    line-height: 1 !important;
  }
  
  .invite-hint-box {
    margin-top: 8px;
    padding: 12px 16px;
    background: var(--bg-page);
    border-radius: 12px;
    border: 1px dashed var(--el-border-color-light);
    display: flex;
    gap: 10px;
    align-items: flex-start;
    color: var(--text-500);
  }
  
  .invite-hint-box p {
    margin: 0;
    font-size: 12px;
    line-height: 1.4;
  }
  
  /* --- 结果页样式 --- */
  .result-container {
    justify-content: center;
    align-items: center;
    padding-top: 20px;
  }
  
  .result-content {
    text-align: center;
    margin-bottom: 32px;
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
  }
  
  .result-icon {
    font-size: 64px;
    margin-bottom: 16px;
    transition: all 0.3s;
  }
  
  .result-icon.success {
    color: var(--el-color-success);
  }
  .result-icon.error {
    color: var(--el-color-danger);
  }
  
  .result-title {
    font-size: 18px;
    font-weight: 800;
    color: var(--text-900);
    margin: 0 0 8px;
  }
  
  .result-message {
    font-size: 14px;
    color: var(--text-500);
    margin: 0;
    max-width: 80%;
    line-height: 1.5;
  }
  
  /* --- 底部按钮 --- */
  .dialog-actions {
    margin-top: auto;
  }
  
  .action-btn-full {
    width: 100%;
    height: 48px;
    font-size: 15px;
    font-weight: 800;
    border-radius: 14px;
  }
  </style>