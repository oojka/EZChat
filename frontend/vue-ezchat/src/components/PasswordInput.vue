<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import { Hide, Lock, View } from '@element-plus/icons-vue'

defineOptions({
  inheritAttrs: false
})

interface Props {
  modelValue: string
  placeholder?: string
  size?: 'large' | 'default' | 'small'
  prefixIcon?: Component
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  size: 'large',
  prefixIcon: Lock
})

const emit = defineEmits(['update:modelValue', 'enter'])

// 使用计算属性实现 v-model 转发
const internalValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const showPassword = ref(false)
</script>

<template>
  <el-input v-bind="$attrs" v-model="internalValue" :type="showPassword ? 'text' : 'password'"
    :placeholder="placeholder" :size="size" class="password-input-wrapper" @copy.prevent @cut.prevent
    @contextmenu.prevent @keydown.enter="emit('enter')">
    <template #prefix>
      <el-icon>
        <component :is="prefixIcon" />
      </el-icon>
    </template>
    <template #suffix>
      <el-icon class="pwd-view-icon" @mousedown="showPassword = true" @mouseup="showPassword = false"
        @mouseleave="showPassword = false" @touchstart.prevent="showPassword = true"
        @touchend.prevent="showPassword = false">
        <View v-if="showPassword" />
        <Hide v-else />
      </el-icon>
    </template>
  </el-input>
</template>

<style scoped>
.password-input-wrapper :deep(.el-input__inner) {
  user-select: none !important;
  -webkit-user-select: none !important;
  -moz-user-select: none !important;
  -ms-user-select: none !important;
  padding: 0 16px 0 0 !important; /* Strict padding control */
  text-indent: 0;
  flex: 1;
}

/* Ensure PasswordInput matches the strict layout of other premium inputs */
.password-input-wrapper :deep(.el-input__wrapper) {
  display: flex;
  align-items: center;
  padding: 0;
}

.password-input-wrapper :deep(.el-input__prefix) {
  margin-right: 0;
  display: flex;
  align-items: center;
  height: 100%;
}

.password-input-wrapper :deep(.el-input__prefix-inner) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px; /* Strict fixed width for icon alignment */
  min-width: 48px;
  height: 100%;
  font-size: 20px;
}

.password-input-wrapper :deep(.el-input__suffix) {
  display: flex;
  align-items: center;
  height: 100%;
  right: 12px;
}

/* 正常状态的背景色 - 已由 main.css 全局托管 */

/* 禁用状态的深度定制样式 - 玻璃拟态锁定效果 */
.password-input-wrapper :deep(.el-input.is-disabled .el-input__wrapper),
.password-input-wrapper :deep(.el-input__wrapper.is-disabled) {
  /* 日间模式：柔和的灰色背景，类似锁定的玻璃 */
  background-color: #f5f7fa !important;
  cursor: not-allowed !important;
  /* 移除 hover 时的边框高亮效果 */
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
}

/* 禁用状态：文字和占位符透明度 */
.password-input-wrapper :deep(.el-input.is-disabled .el-input__inner),
.password-input-wrapper :deep(.el-input__wrapper.is-disabled .el-input__inner) {
  color: var(--text-900) !important;
  opacity: 0.5 !important;
  cursor: not-allowed !important;
}

/* 禁用状态：左侧图标透明度 */
.password-input-wrapper :deep(.el-input.is-disabled .el-input__prefix .el-icon),
.password-input-wrapper :deep(.el-input.is-disabled .el-input__prefix-inner .el-icon) {
  opacity: 0.5 !important;
}

/* 禁用状态：占位符透明度 */
.password-input-wrapper :deep(.el-input.is-disabled .el-input__inner::placeholder),
.password-input-wrapper :deep(.el-input__wrapper.is-disabled .el-input__inner::placeholder) {
  opacity: 0.5 !important;
}

/* 禁用状态：移除 hover 效果 */
.password-input-wrapper :deep(.el-input.is-disabled:hover .el-input__wrapper),
.password-input-wrapper :deep(.el-input__wrapper.is-disabled:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  border-color: transparent !important;
}

/* 黑夜模式：禁用状态样式 */
html.dark .password-input-wrapper :deep(.el-input.is-disabled .el-input__wrapper),
html.dark .password-input-wrapper :deep(.el-input__wrapper.is-disabled) {
  /* Dark Mode: Disabled input background */
  background-color: #1d1e1f !important;
  cursor: not-allowed !important;
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
}

/* 黑夜模式：禁用状态 hover 移除 */
html.dark .password-input-wrapper :deep(.el-input.is-disabled:hover .el-input__wrapper),
html.dark .password-input-wrapper :deep(.el-input__wrapper.is-disabled:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  border-color: transparent !important;
}

.pwd-view-icon {
  cursor: pointer;
  color: var(--text-400);
  transition: color 0.2s;
  font-size: 18px;
  padding: 4px;
}

.pwd-view-icon:hover {
  color: var(--primary);
}
</style>
