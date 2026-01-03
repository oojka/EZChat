<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { Loading } from '@element-plus/icons-vue'

/**
 * SmartAvatar：带加载占位 + 严格 3 段 fallback 的头像组件
 *
 * 3 段策略（严格）：
 * - Thumbnail -> Original -> Text/Placeholder
 *
 * 说明：
 * - 使用本地响应式 currentUrl，并绑定 :key="currentUrl" 强制重新渲染
 * - 通过 @error 事件实现从缩略图切换到原图，再回退到文字
 * - 通过 el-image 的 placeholder 显示“转圈加载”
 */

type Shape = 'circle' | 'square'

const props = withDefaults(defineProps<{
  size?: number
  shape?: Shape
  thumbUrl?: string
  url?: string
  text?: string
}>(), {
  size: 40,
  shape: 'square',
  thumbUrl: '',
  url: '',
  text: '?',
})

const currentUrl = ref<string>('')
const showText = ref(false)

watchEffect(() => {
  // 初始化：优先缩略图，其次原图
  const thumb = props.thumbUrl || ''
  const original = props.url || ''
  currentUrl.value = thumb || original || ''
  showText.value = !currentUrl.value
})

/**
 * 图片加载失败处理：
 * - 若当前是缩略图且存在原图，则切到原图（返回 false 的语义：继续尝试加载）
 * - 否则回退到文字占位（返回 true 的语义：结束，显示文字）
 */
const handleError = () => {
  const thumb = props.thumbUrl || ''
  const original = props.url || ''

  if (currentUrl.value && currentUrl.value === thumb && original) {
    currentUrl.value = original
    return false
  }

  // 原图也失败（或没有原图）：回退到文字
  currentUrl.value = ''
  showText.value = true
  return true
}

const wrapperStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  '--size': `${props.size}px`,
}))

const shapeClass = computed(() => (props.shape === 'circle' ? 'is-circle' : 'is-square'))

const firstChar = computed(() => (props.text || '?').trim().charAt(0) || '?')
</script>

<template>
  <div class="smart-avatar" :class="shapeClass" :style="wrapperStyle">
    <el-image
      v-if="!showText && currentUrl"
      :key="currentUrl"
      class="avatar-img"
      :src="currentUrl"
      fit="cover"
      @error="handleError"
    >
      <template #placeholder>
        <div class="avatar-placeholder">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
      </template>
      <template #error>
        <div class="avatar-placeholder">{{ firstChar }}</div>
      </template>
    </el-image>

    <div v-else class="avatar-text">{{ firstChar }}</div>
  </div>
</template>

<style scoped>
.smart-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--bg-page);
  border: 1px solid var(--el-border-color-light);
  color: var(--text-500);
}

.smart-avatar.is-square { 
  --avatar-size: var(--size);
  border-radius: calc(var(--avatar-size) * var(--avatar-border-radius-ratio));
}
.smart-avatar.is-circle { border-radius: 50%; }

.avatar-img {
  width: 100%;
  height: 100%;
  display: block;
}

.avatar-placeholder,
.avatar-text {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 12px;
}
</style>


