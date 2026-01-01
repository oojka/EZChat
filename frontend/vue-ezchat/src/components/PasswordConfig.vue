<script setup lang="ts">
import { computed } from 'vue'
import { Lock } from '@element-plus/icons-vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { useI18n } from 'vue-i18n'

interface Props {
  modelValue: number // joinEnable: 0 | 1
  password: string
  passwordConfirm: string
  hasPasswordError?: boolean
  passwordErrorMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  hasPasswordError: false,
  passwordErrorMessage: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: number]
  'update:password': [value: string]
  'update:passwordConfirm': [value: string]
  'enter': []
}>()

const { t } = useI18n()

const joinEnable = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const passwordValue = computed({
  get: () => props.password,
  set: (val) => emit('update:password', val),
})

const passwordConfirmValue = computed({
  get: () => props.passwordConfirm,
  set: (val) => emit('update:passwordConfirm', val),
})

const handleEnter = () => {
  emit('enter')
}
</script>

<template>
  <div class="password-form-center">
    <div class="config-glass-card">
      <div class="config-header">
        <div class="title-with-icon">
          <el-icon><Lock /></el-icon>
          <span>{{ t('create_chat.password_join') }}</span>
        </div>
        <el-switch
          v-model="joinEnable"
          :active-value="1"
          :inactive-value="0"
          :active-text="t('common.on')"
          :inactive-text="t('common.off')"
          inline-prompt
        />
      </div>

      <!-- 密码功能说明提示（始终可见，支持语言切换淡入淡出） -->
      <Transition name="el-fade-in-linear" mode="out-in">
        <p :key="t('create_chat.password_info_desc')" class="password-hint">
          {{ t('create_chat.password_info_desc') }}
        </p>
      </Transition>

      <!-- 密码输入框（展开时显示） -->
      <Transition name="password-input-expand">
        <div v-if="joinEnable === 1" class="password-setup-area">
          <div class="password-inputs-grid">
            <el-form-item :label="t('auth.password')" prop="password" :show-message="false">
              <PasswordInput
                v-model="passwordValue"
                :placeholder="t('auth.password')"
                @enter="handleEnter"
              />
            </el-form-item>
            <el-form-item :label="t('auth.confirm_password')" prop="passwordConfirm" :show-message="false">
              <PasswordInput
                v-model="passwordConfirmValue"
                :placeholder="t('auth.confirm_password_placeholder')"
                @enter="handleEnter"
              />
            </el-form-item>
          </div>
          <!-- 固定高度的错误提示容器 -->
          <div class="password-error-container">
            <Transition name="el-fade-in-linear">
              <span v-show="hasPasswordError" class="password-error-text">
                {{ passwordErrorMessage }}
              </span>
            </Transition>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<style scoped>
.password-form-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  width: 100%;
}

.password-form-center .config-glass-card {
  width: 100%;
}

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

.password-hint {
  margin-top: 12px;
  margin-bottom: 0;
  font-size: 11px;
  color: var(--text-500);
  line-height: 1.6;
  white-space: pre-line;
}

.password-setup-area {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-extra-light);
  overflow: hidden;
}

.password-inputs-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.password-error-container {
  height: 20px;
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
}

.password-error-text {
  font-size: 11px;
  color: var(--el-color-danger);
  font-weight: 600;
  line-height: 1.2;
}

/* Password input wrapper */
:deep(.password-input-wrapper .el-input__wrapper) {
  height: 48px !important;
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

/* --- Password Animation Transitions --- */
/* 密码输入框展开/收起动画 */
.password-input-expand-enter-active {
  transition: opacity 0.3s var(--ease-out-expo), transform 0.3s var(--ease-out-expo);
  transition-delay: 0.25s;
}

.password-input-expand-leave-active {
  transition: opacity 0.3s var(--ease-out-expo), transform 0.3s var(--ease-out-expo);
}

.password-input-expand-enter-from {
  opacity: 0;
  transform: translateY(-10px) scale(0.96);
}

.password-input-expand-enter-to {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.password-input-expand-leave-from {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.password-input-expand-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.96);
}
</style>

