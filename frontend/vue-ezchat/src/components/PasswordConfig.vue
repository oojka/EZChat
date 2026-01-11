<script setup lang="ts">
/**
 * 密码保护配置组件
 * 
 * 功能：提供密码保护开关和密码输入框的UI组件
 * 特性：支持展开/收起模式、国际化、表单验证集成
 * 
 * 注意：本组件仅负责UI渲染，不维护校验状态，依赖外层el-form rules进行验证
 */
import { computed } from 'vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { useI18n } from 'vue-i18n'

/**
 * 组件属性接口
 */
interface Props {
  /** 密码保护开关值：0=禁用，1=启用 */
  modelValue: number
  /** 密码值 */
  password: string
  /** 确认密码值 */
  passwordConfirm: string
  /** 显示模式：expand=开关控制展开/收起，always-visible=始终显示但开关控制是否可输入 */
  mode?: 'expand' | 'always-visible'
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'expand',
})

/**
 * 组件事件定义
 */
const emit = defineEmits<{
  /** 更新密码保护开关值 */
  'update:modelValue': [value: number]
  /** 更新密码值 */
  'update:password': [value: string]
  /** 更新确认密码值 */
  'update:passwordConfirm': [value: string]
  /** 回车键事件 */
  'enter': []
}>()

const { t } = useI18n()

/**
 * 密码保护开关的计算属性（双向绑定）
 */
const joinEnableByPassword = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/**
 * 密码输入框的计算属性（双向绑定）
 */
const passwordValue = computed({
  get: () => props.password,
  set: (val) => emit('update:password', val),
})

/**
 * 确认密码输入框的计算属性（双向绑定）
 */
const passwordConfirmValue = computed({
  get: () => props.passwordConfirm,
  set: (val) => emit('update:passwordConfirm', val),
})

/**
 * 处理回车键事件
 */
const handleEnter = () => {
  emit('enter')
}
</script>

<template>
  <div class="password-form-center">
    <div class="config-glass-card">
      <div class="config-header">
        <!-- 弱化标题：只保留开关，标题改为轻提示 -->
        <span class="header-label">{{ t('create_chat.password_join') }}</span>
        <el-switch
          v-model="joinEnableByPassword"
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

      <!-- 密码输入框 -->
      <!-- expand 模式：展开时显示 -->
      <Transition v-if="mode === 'expand'" name="password-input-expand">
        <div v-if="joinEnableByPassword === 1" class="password-setup-area">
          <div class="password-inputs-grid">
            <!-- 使用 el-form-item 原生错误展示 -->
            <el-form-item prop="password" class="no-label-item show-error-inline">
              <PasswordInput
                v-model="passwordValue"
                :placeholder="t('auth.password')"
                @enter="handleEnter"
              />
            </el-form-item>
            <el-form-item prop="passwordConfirm" class="no-label-item show-error-inline">
              <PasswordInput
                v-model="passwordConfirmValue"
                :placeholder="t('auth.confirm_password_placeholder')"
                @enter="handleEnter"
              />
            </el-form-item>
          </div>
        </div>
      </Transition>
      
      <!-- always-visible 模式：始终显示，开关控制是否可输入 -->
      <div v-else class="password-setup-area">
        <div class="password-inputs-grid">
          <!-- 使用 el-form-item 原生错误展示 -->
          <el-form-item prop="password" class="no-label-item show-error-inline">
            <PasswordInput
              v-model="passwordValue"
              :placeholder="t('auth.password')"
              :disabled="joinEnableByPassword === 0"
              @enter="handleEnter"
            />
          </el-form-item>
          <el-form-item prop="passwordConfirm" class="no-label-item show-error-inline">
            <PasswordInput
              v-model="passwordConfirmValue"
              :placeholder="t('auth.confirm_password_placeholder')"
              :disabled="joinEnableByPassword === 0"
              @enter="handleEnter"
            />
          </el-form-item>
        </div>
      </div>
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

/* 弱化标题：轻提示风格，替代原 icon + 粗体标题 */
.header-label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-500);
  letter-spacing: 0.2px;
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
  margin-bottom: 0;
}

/* 让 el-form-item 显示校验错误信息 */
.show-error-inline :deep(.el-form-item__error) {
  font-size: 11px;
  font-weight: 600;
  padding-top: 4px;
}

/* Password input wrapper - 对齐登录页风格 */
.password-form-center :deep(.password-input-wrapper .el-input__wrapper) {
  height: 48px !important;
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset;
  background-color: var(--bg-page);
  padding: 6px 16px;
  transition: all 0.3s;
}

.password-form-center :deep(.password-input-wrapper .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

:deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 在 password-inputs-grid 中的 form-item 移除最后一个的 margin-bottom */
.password-inputs-grid :deep(.el-form-item:last-of-type) {
  margin-bottom: 0 !important;
}

/* 确保 password-inputs-grid 内所有 form-item 的内部结构也没有额外的 margin */
.password-inputs-grid :deep(.el-form-item:last-of-type .el-form-item__content) {
  margin-bottom: 0 !important;
}

:deep(.el-form-item__label) {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-700);
  padding-bottom: 4px !important;
  line-height: 1 !important;
}

/* 去除 label：对齐登录页风格，用 placeholder 替代 */
.no-label-item :deep(.el-form-item__label) {
  display: none !important;
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

