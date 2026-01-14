<script setup lang="ts">
/**
 * 二维码展示对话框组件
 *
 * 功能：
 * - 将 URL 转换为二维码图片展示
 * - 提供 URL 文本显示与复制（虽然主要是展示）
 * - 玻璃拟态风格 UI
 */
import { ref, watch, nextTick } from 'vue'
import QRCode from 'qrcode'
import { Close } from '@element-plus/icons-vue'

interface Props {
  modelValue: boolean
  url: string
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const qrCodeUrl = ref('')

/**
 * 生成二维码图片 URL
 */
const generateQrCode = async () => {
  if (!props.url) {
    qrCodeUrl.value = ''
    return
  }
  try {
    qrCodeUrl.value = await QRCode.toDataURL(props.url, {
      width: 200,
      margin: 2,
      color: {
        dark: '#000000',
        light: '#ffffff',
      },
    })
  } catch (err) {
    console.error('QR Generation failed', err)
    qrCodeUrl.value = ''
  }
}

// 监听 URL 变化重新生成
watch(() => props.url, generateQrCode)

// 监听打开状态，打开时确保生成
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      nextTick(generateQrCode)
    }
  },
  { immediate: true },
)

const handleClose = () => {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
    width="320px"
    class="ez-modern-dialog qr-code-dialog"
    align-center
    :show-close="false"
    append-to-body
  >
    <template #header>
      <div class="qr-header">
        <h4 v-if="title" class="qr-title">{{ title }}</h4>
        <div class="ez-dialog-header-actions">
          <button class="ez-close-btn" type="button" @click="handleClose">
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </div>
    </template>

    <div class="qr-content">
      <div class="qr-image-wrapper">
        <img v-if="qrCodeUrl" :src="qrCodeUrl" alt="QR Code" class="qr-img" />
        <div v-else class="qr-placeholder">
          <span>Generating...</span>
        </div>
      </div>
      <p class="qr-url-text" :title="url">{{ url }}</p>
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

/* 重置 Element Plus header */
:deep(.el-dialog__header) {
  padding: 0 !important;
  margin: 0 !important;
}

:deep(.el-dialog__body) {
  padding: 0 !important;
}

/* --- Layout --- */
.qr-header {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px 24px 0;
  min-height: 32px;
}

.qr-title {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: var(--text-900);
}

/* 确保关闭按钮位置正确 */
:deep(.ez-dialog-header-actions) {
  position: absolute;
  right: 16px;
  top: 16px;
  z-index: 10;
}

/* 如果全局样式未生效，这里兜底 (Though it should work from ez-dialog.css) */
.ez-close-btn {
  background: transparent;
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

.ez-close-btn:hover {
  background: var(--el-border-color-light);
  color: var(--text-900);
  transform: rotate(90deg);
}

.qr-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  gap: 16px;
}

.qr-image-wrapper {
  width: 200px;
  height: 200px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: #ffffff; /* QR Code needs white bg */
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.qr-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.qr-placeholder {
  color: var(--text-400);
  font-size: 12px;
  font-weight: 600;
}

.qr-url-text {
  max-width: 100%;
  font-size: 12px;
  color: var(--text-500);
  text-align: center;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background: var(--bg-input);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-glass);
  width: 100%;
  box-sizing: border-box;
}
</style>
