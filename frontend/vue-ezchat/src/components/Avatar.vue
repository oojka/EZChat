<script setup lang="ts">
/**
 * 统一头像组件
 * 
 * 功能：显示用户或群组头像，支持智能图片加载、占位符、编辑模式
 * 特性：缩略图优先、GIF特殊处理、加载失败降级、可编辑遮罩
 */
import { computed, ref, watch } from 'vue'
import { Camera, Loading, Picture } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import type { Image } from '@/type'

const { t } = useI18n()

/** 头像形状类型 */
type Shape = 'circle' | 'square'

/** 组件属性接口 */
type Props = {
  /** 图片对象，包含URL和缩略图信息 */
  image?: Image
  /** 显示文本（当无图片时显示首字符） */
  text?: string

  // 样式配置
  /** 头像尺寸，支持数字(px)或字符串单位 */
  size?: number | string
  /** 头像形状：圆形或方形 */
  shape?: Shape
  /** 圆角比例（仅当shape为square时生效） */
  borderRadiusRatio?: number

  // 交互配置
  /** 是否可编辑（显示悬停遮罩） */
  editable?: boolean
  /** 占位图标尺寸 */
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

/** 当前显示的图片URL */
const currentUrl = ref<string>('')
/** 是否加载失败 */
const isError = ref(false)
/** 是否正在加载 */
const isLoading = ref(false)
/** 加载序列号，用于防止竞态条件 */
let loadSeq = 0

/**
 * 判断图片是否为GIF格式
 * @param image 图片对象
 * @returns 是否为GIF
 */
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

/**
 * 获取图片URL候选列表（主URL和备选URL）
 * @param image 图片对象
 * @returns 包含主URL和备选URL的对象
 */
const getUrlCandidates = (image?: Image) => {
  if (!image) {
    return { primary: '', fallback: '' }
  }

  const thumb = image.blobThumbUrl || image.imageThumbUrl || ''
  const original = image.blobUrl || image.imageUrl || ''
  const isGif = isGifImage(image)

  // GIF文件特殊处理：优先使用原图
  if (isGif) {
    return {
      primary: original || thumb,
      fallback: original && thumb && original !== thumb ? thumb : ''
    }
  }

  // 非GIF文件：优先使用缩略图
  return {
    primary: thumb || original,
    fallback: thumb && original && thumb !== original ? original : ''
  }
}

/**
 * 预加载图片并切换到最佳可用URL
 * @param urls URL候选列表
 */
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

// 监听图片属性变化，重新加载图片
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

/**
 * 处理图片加载错误
 */
const handleError = () => {
  currentUrl.value = ''
  isError.value = true
  isLoading.value = false
}

/**
 * 标准化尺寸，将数字转换为px单位字符串
 * @param s 尺寸值
 * @returns 标准化后的尺寸字符串
 */
const normalizeSize = (s: number | string) => (typeof s === 'number' ? `${s}px` : s)

/**
 * 容器样式计算属性
 */
const containerStyle = computed(() => ({
  width: normalizeSize(props.size),
  height: normalizeSize(props.size),
  '--avatar-radius': props.shape === 'circle'
    ? '50%'
    : `calc(${normalizeSize(props.size)} * ${props.borderRadiusRatio})`
}))

/**
 * 首字符计算属性（用于占位显示）
 */
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
