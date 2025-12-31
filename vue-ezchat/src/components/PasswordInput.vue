<script setup lang="ts">
import {computed, ref} from 'vue'
import {Hide, Lock, View} from '@element-plus/icons-vue'

defineOptions({
  inheritAttrs: false
})

interface Props {
  modelValue: string
  placeholder?: string
  size?: 'large' | 'default' | 'small'
  prefixIcon?: any
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
  <el-input
    v-bind="$attrs"
    v-model="internalValue"
    :type="showPassword ? 'text' : 'password'"
    :placeholder="placeholder"
    :size="size"
    class="password-input-wrapper"
    @copy.prevent
    @cut.prevent
    @contextmenu.prevent
    @keydown.enter="emit('enter')"
  >
    <template #prefix>
      <el-icon><component :is="prefixIcon" /></el-icon>
    </template>
    <template #suffix>
      <el-icon
        class="pwd-view-icon"
        @mousedown="showPassword = true"
        @mouseup="showPassword = false"
        @mouseleave="showPassword = false"
        @touchstart.prevent="showPassword = true"
        @touchend.prevent="showPassword = false"
      >
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
