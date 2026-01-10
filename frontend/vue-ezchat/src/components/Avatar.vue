<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Camera, Loading, Picture } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { Image } from '@/type'

/**
 * Avatar: 统一的用户/群组头像组件
 *
 * 功能：
 * 1. 智能 Fallback: 缩略图 -> 原图 -> 文字/图标（GIF 直接原图）
 * 2. 统一样式: 支持 circle/square, 统一圆角比例
 * 3. 上传模式: editable=true 时显示悬停遮罩
 * 4. 占位模式: 无图片时显示虚线框占位 (isPlaceholder)
 */

const { t } = useI18n()

type Shape = 'circle' | 'square'

type Props = {
  image?: Image
  text?: string

  // 样式配置
  size?: number | string
  shape?: Shape
  borderRadiusRatio?: number

  // 交互配置
  editable?: boolean
  iconSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 100,
  shape: 'square',
  borderRadiusRatio: 0.3,
  editable: false,
  iconSize: 40,
  text: '?',
})

const currentUrl = ref<string>('')
const isError = ref(false)
const isLoading = ref(false)
let loadSeq = 0

const isGifImage = (image?: Image): boolean => {
  if (!image) return false
  const name = image.imageName || ''
  const url = image.imageUrl || ''
  const thumbUrl = image.imageThumbUrl || ''
  const blobUrl = image.blobUrl || ''
  const blobThumbUrl = image.blobThumbUrl || ''
  const hint = `${name} ${url} ${thumbUrl} ${blobUrl} ${blobThumbUrl}`.toLowerCase()
  return hint.includes('.gif')
}

const getUrlCandidates = (image?: Image) => {
  if (!image) {
    return { primary: '', fallback: '' }
  }

  const thumb = image.blobThumbUrl || image.imageThumbUrl || ''
  const original = image.blobUrl || image.imageUrl || ''
  const isGif = isGifImage(image)

  if (isGif) {
    return {
      primary: original || thumb,
      fallback: original && thumb && original !== thumb ? thumb : ''
    }
  }

  return {
    primary: thumb || original,
    fallback: thumb && original && thumb !== original ? original : ''
  }
}

const preloadAndSwap = (urls: string[]) => {
  if (!urls.length) {
    currentUrl.value = ''
    isError.value = true
    isLoading.value = false
    return
  }

  const primary = urls[0]
  if (primary === currentUrl.value && currentUrl.value) {
    isError.value = false
    isLoading.value = false
    return
  }

  const seq = ++loadSeq
  isLoading.value = true
  isError.value = false

  const tryLoad = (index: number) => {
    const target = urls[index]
    if (!target) {
      if (seq === loadSeq) {
        currentUrl.value = ''
        isError.value = true
        isLoading.value = false
      }
      return
    }
    const img = new Image()
    img.onload = () => {
      if (seq !== loadSeq) return
      currentUrl.value = target
      isError.value = false
      isLoading.value = false
    }
    img.onerror = () => {
      if (seq !== loadSeq) return
      if (index + 1 < urls.length) {
        tryLoad(index + 1)
        return
      }
      currentUrl.value = ''
      isError.value = true
      isLoading.value = false
    }
    img.src = target
  }

  tryLoad(0)
}

watch(
  () => props.image,
  () => {
    const { primary, fallback } = getUrlCandidates(props.image)
    if (primary && primary === currentUrl.value && !isError.value) {
      return
    }
    const urls: string[] = []
    if (primary) urls.push(primary)
    if (fallback && fallback !== primary) urls.push(fallback)
    preloadAndSwap(urls)
  },
  { immediate: true }
)

const handleError = () => {
  currentUrl.value = ''
  isError.value = true
  isLoading.value = false
}

const normalizeSize = (s: number | string) => (typeof s === 'number' ? `${s}px` : s)

const containerStyle = computed(() => ({
  width: normalizeSize(props.size),
  height: normalizeSize(props.size),
  '--avatar-radius': props.shape === 'circle'
    ? '50%'
    : `calc(${normalizeSize(props.size)} * ${props.borderRadiusRatio})`
}))

const firstChar = computed(() => (props.text || '?').trim().charAt(0).toUpperCase() || '?')
</script>

<template>
  <div class="ez-user-avatar" :class="[
    `shape-${shape}`,
    { 'is-editable': editable, 'is-placeholder-mode': isError }
  ]" :style="containerStyle">
    <!-- 图片模式 -->
    <el-image v-if="!isError && currentUrl" :src="currentUrl" fit="cover" class="avatar-img" @error="handleError">
      <template #placeholder>
        <div class="image-slot loading">
          <el-icon class="is-loading">
            <Loading />
          </el-icon>
        </div>
      </template>
      <template #error>
        <div class="image-slot error">
          <span>{{ firstChar }}</span>
        </div>
      </template>
    </el-image>

    <!-- 占位模式 (无图或加载失败) -->
    <div v-else class="avatar-placeholder">
      <div v-if="isLoading" class="image-slot loading">
        <el-icon class="is-loading">
          <Loading />
        </el-icon>
      </div>
      <template v-else>
        <span v-if="text && text !== '?'" class="placeholder-text">{{ firstChar }}</span>
        <div v-else class="placeholder-icon-wrapper">
          <el-icon :size="iconSize">
            <Picture />
          </el-icon>
          <span v-if="editable" class="placeholder-hint">
            {{ t('auth.select_image') }}
          </span>
        </div>
      </template>
    </div>

    <!-- 编辑遮罩 (Hover) -->
    <div v-if="editable" class="edit-mask">
      <el-icon>
        <Camera />
      </el-icon>
      <span>{{ t('common.change') }}</span>
    </div>
  </div>
</template>

<style scoped>
.ez-user-avatar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  border-radius: var(--avatar-radius);
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.ez-user-avatar.is-placeholder-mode {
  border: 2px dashed var(--el-border-color);
  background: var(--bg-fill-0);
  color: var(--text-400);
}

.ez-user-avatar.is-placeholder-mode:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light-9);
}

.avatar-img {
  width: 100%;
  height: 100%;
  display: block;
}

.image-slot {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-fill-1);
  color: var(--text-400);
  font-size: 14px;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.placeholder-text {
  font-size: 16px;
  font-weight: 800;
  color: var(--text-700);
}

.placeholder-icon-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--text-400);
}

.placeholder-hint {
  font-size: 10px;
  font-weight: 700;
}

.edit-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  opacity: 0;
  transition: all 0.2s ease;
  font-size: 12px;
}

.ez-user-avatar.is-editable:hover .edit-mask {
  opacity: 1;
}
</style>
