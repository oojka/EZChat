<script setup lang="ts">
import {
  Calendar,
  Camera,
  EditPen,
  InfoFilled,
  Link,
  Lock,
  Plus,
  User
} from '@element-plus/icons-vue'
import {useCreateChat} from '@/hooks/useCreateChat.ts'
import {storeToRefs} from 'pinia'
import {useAppStore} from '@/stores/appStore.ts'
import PasswordInput from '@/components/PasswordInput.vue'

const appStore = useAppStore()
const { createRoomVisible } = storeToRefs(appStore)

const {
  createChatForm, handleCreate, createFormRef, createFormRules,
  beforeAvatarUpload, handleAvatarSuccess
} = useCreateChat()

const disabledDate = (time: Date) => {
  const now = new Date()
  const startOfToday = new Date(now.setHours(0, 0, 0, 0))
  const thirtyDaysLater = new Date(startOfToday.getTime() + 30 * 24 * 60 * 60 * 1000)
  return time.getTime() < startOfToday.getTime() || time.getTime() > thirtyDaysLater.getTime()
}
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
          <h3>ルーム新規作成</h3>
          <p>新しいコミュニティの始まりです</p>
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
            <el-upload class="avatar-uploader" action="/api/auth/register/upload" :show-file-list="false" :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload">
              <div v-if="createChatForm.avatar.objectThumbUrl" class="avatar-preview-lg">
                <img :src="createChatForm.avatar.objectThumbUrl" class="avatar-img" />
                <div class="edit-mask-lg"><el-icon><Camera /></el-icon><span>変更</span></div>
              </div>
              <div v-else class="placeholder-circle-lg"><el-icon size="28"><Camera /></el-icon><span>アイコン</span></div>
            </el-upload>
            <div class="left-panel-hint">※推奨: 500px以上</div>
          </div>

          <div class="room-rules-block">
            <div class="rules-header"><el-icon><InfoFilled /></el-icon><span>ルーム設定ガイド</span></div>
            <div class="rules-list">
              <div class="rule-item"><el-icon class="rule-icon"><Lock /></el-icon><div class="rule-content"><span class="rule-title">参加制限</span><span class="rule-desc">PW設定で検索可能に</span></div></div>
              <div class="rule-item"><el-icon class="rule-icon"><Link /></el-icon><div class="rule-content"><span class="rule-title">招待URL</span><span class="rule-desc">最大30日まで有効</span></div></div>
              <div class="rule-item"><el-icon class="rule-icon"><User /></el-icon><div class="rule-content"><span class="rule-title">定員数</span><span class="rule-desc">最大人数は无制限</span></div></div>
            </div>
          </div>
        </div>

        <div class="form-right-panel">
          <div class="section-group">
            <el-form-item label="ルーム名" prop="chatName" class="mb-0">
              <el-input v-model="createChatForm.chatName" placeholder="チャットルームの名前を入力" class="custom-input name-input-lg" />
            </el-form-item>
          </div>

          <div class="config-cards-stack">
            <div class="config-card-flat security-card">
              <div class="card-title-row space-between">
                <div class="flex-center gap-2"><el-icon><Lock /></el-icon><span>パスワードで加入可能</span></div>
                <el-switch v-model="createChatForm.joinEnable" :active-value="1" :inactive-value="0" active-text="ON" inactive-text="OFF" inline-prompt />
              </div>
              <transition name="el-zoom-in-top">
                <div v-if="createChatForm.joinEnable === 1" class="password-fields">
                  <div class="password-grid">
                    <el-form-item label="パスワード" prop="password" class="mb-0">
                      <PasswordInput
                        v-model="createChatForm.password"
                        placeholder="パスワード"
                        size="default"
                      />
                    </el-form-item>
                    <el-form-item label="确认用" prop="passwordConfirm" class="mb-0">
                      <PasswordInput
                        v-model="createChatForm.passwordConfirm"
                        placeholder="再入力"
                        size="default"
                      />
                    </el-form-item>
                  </div>
                </div>
              </transition>
            </div>

            <div class="config-card-flat expiry-card">
              <div class="card-title-row"><el-icon><Calendar /></el-icon><span>招待リンクの有効期限</span></div>
              <div class="expiry-vertical-layout">
                <el-radio-group v-model="createChatForm.joinLinkExpiry" class="modern-radios-compact full-width">
                  <el-radio-button label="1">1日</el-radio-button>
                  <el-radio-button label="7">7日</el-radio-button>
                  <el-radio-button label="30">30日</el-radio-button>
                </el-radio-group>
                <div class="custom-date-wrapper full-width">
                  <el-date-picker v-model="createChatForm.joinLinkExpiry" type="datetime" placeholder="カスタム日時を指定" format="YYYY/MM/DD HH:mm" value-format="YYYY-MM-DD HH:mm:00" :disabled-date="disabledDate" :prefix-icon="EditPen" class="custom-picker-inline" style="width: 100%" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer footer-wide">
        <el-button @click="createRoomVisible = false" class="btn-cancel-wide">キャンセル</el-button>
        <el-button type="primary" @click="handleCreate" class="btn-submit create-btn-final">ルームを立ち上げる</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
:deep(.modern-dialog) { border-radius: 28px; overflow: hidden; box-shadow: var(--shadow-glass); border: 1px solid var(--border-glass); background: var(--bg-card); }
.dialog-banner { height: 70px; padding: 0 32px; color: #fff; display: flex; align-items: center; position: relative; overflow: hidden; background: linear-gradient(135deg, var(--primary) 0%, #3a8ee6 100%); }
.banner-content h3 { font-size: 20px; font-weight: 900; margin: 0; }
.banner-icon { position: absolute; right: -10px; bottom: -20px; font-size: 100px; opacity: 0.12; transform: rotate(-12deg); }
.close-btn { position: absolute; right: 20px; top: 20px; font-size: 20px; cursor: pointer; opacity: 0.6; }

.create-form-horizontal { display: flex; background: var(--bg-card); min-height: 360px; }
.form-left-panel { width: 240px; padding: 24px 20px; display: flex; flex-direction: column; justify-content: center; align-items: center; background-color: var(--bg-aside); border-right: 1px solid var(--el-border-color-light); }
.form-right-panel { flex: 1; padding: 24px 32px; display: flex; flex-direction: column; gap: 16px; background-color: var(--bg-card); }

.avatar-preview-lg, .placeholder-circle-lg { width: 100px; height: 100px; border-radius: 24px; overflow: hidden; cursor: pointer; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06); position: relative; background: var(--bg-page); }
.room-rules-block { width: 100%; background: var(--bg-page); border-radius: 16px; padding: 14px; border: 1px solid var(--el-border-color-light); }
.rules-header { font-size: 11px; font-weight: 800; color: var(--text-700); margin-bottom: 10px; }
.rule-title { font-size: 10px; font-weight: 700; color: var(--text-900); }
.rule-desc { font-size: 9px; color: var(--text-500); }

.config-card-flat { border-radius: 14px; padding: 14px 18px; background: var(--bg-card); border: 1px solid var(--el-border-color-light); }
.security-card { background-color: var(--bg-page); }
.card-title-row { font-size: 12px; font-weight: 800; color: var(--text-700); }

:deep(.el-form-item__label) { font-weight: 800; color: var(--text-700); font-size: 11px; }
:deep(.el-input__wrapper) { height: 44px !important; border-radius: 12px !important; box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important; background-color: var(--bg-page) !important; }
:deep(.el-input__inner) { color: var(--text-900); }
:deep(.name-input-lg .el-input__inner) { font-weight: 900; font-size: 15px; }

:deep(.modern-radios-compact .el-radio-button) { flex: 1; margin-right: 8px; }
:deep(.modern-radios-compact .el-radio-button:last-child) { margin-right: 0; }
:deep(.modern-radios-compact .el-radio-button__inner) { width: 100%; height: 36px; background: var(--bg-page); border: 1px solid var(--el-border-color-light) !important; border-radius: 10px !important; color: var(--text-500); font-weight: 700; display: flex; align-items: center; justify-content: center; transition: all 0.3s; font-size: 12px; }
:deep(.modern-radios-compact .el-radio-button__original-radio:checked + .el-radio-button__inner) { background-color: var(--primary) !important; border-color: var(--primary) !important; color: #fff; box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2) !important; }

.footer-wide { padding: 12px 32px 32px; display: flex; justify-content: flex-end; background: var(--bg-card); }
.btn-cancel-wide { height: 44px; padding: 0 24px; border-radius: 12px; background: var(--bg-page); border: none; color: var(--text-500); font-weight: 700; margin-right: 12px; }
.create-btn-final { height: 44px; padding: 0 32px; border-radius: 12px; font-weight: 800; font-size: 14px; background: var(--primary); border: none; color: #fff; box-shadow: 0 8px 16px rgba(64, 158, 255, 0.2); }

.mb-0 { margin-bottom: 0 !important; }
.avatar-uploader :deep(.el-upload) { border: none; background: transparent; cursor: pointer; position: relative; overflow: visible; }
</style>
