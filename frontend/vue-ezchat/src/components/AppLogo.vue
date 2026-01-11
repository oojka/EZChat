<script setup lang="ts">
/**
 * 应用Logo组件
 * 
 * 功能：显示EZ Chat应用Logo，支持多种样式配置
 * 特性：响应式设计、暗黑模式适配、悬停效果、点击导航
 */
import {computed} from 'vue'
import {useRouter} from 'vue-router'

/**
 * 组件属性定义
 */
const props = withDefaults(
  defineProps<{
    /** Logo尺寸，支持数字(px)或字符串单位 */
    size?: number | string
    /** 文字颜色 */
    color?: string
    /** 是否垂直布局（图标在上，文字在下） */
    vertical?: boolean
    /** 是否显示图标容器的玻璃态阴影效果 */
    showShadow?: boolean
    /** 是否可点击（点击返回首页） */
    clickable?: boolean
  }>(),
  {
    size: 36,
    color: 'var(--.gitignore-900)',
    vertical: false,
    showShadow: true,
    clickable: true,
  },
)

const router = useRouter()

/**
 * 计算图标尺寸，将数字转换为px单位字符串
 */
const iconSize = computed(() => typeof props.size === 'number' ? `${props.size}px` : props.size)

/**
 * 处理点击事件，如果可点击则导航到首页
 */
const handleClick = () => {
  if (props.clickable) router.push('/')
}
</script>

<template>
  <div
    class="app-logo-wrapper"
    :class="{ vertical, 'is-clickable': clickable }"
    :style="{ '--icon-size': iconSize, '--text-color': color }"
    @click="handleClick"
  >
    <div class="logo-icon-container" :class="{ 'has-shadow': showShadow }">
      <div class="logo-icon">
        <img src="/favicon_io/android-chrome-192x192.png" alt="EZ Chat" />
      </div>
    </div>
    <div class="logo-text-container">
      <span class="logo-text">EZ Chat</span>
    </div>
  </div>
</template>

<style scoped>
.app-logo-wrapper {
  display: inline-flex; align-items: center; gap: calc(var(--icon-size) * 0.3);
  user-select: none; -webkit-user-select: none; -moz-user-select: none; -ms-user-select: none;
  background: transparent; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-logo-wrapper.vertical { flex-direction: column; gap: calc(var(--icon-size) * 0.2); }

.logo-icon-container {
  display: flex; align-items: center; justify-content: center;
  padding: calc(var(--icon-size) * 0.15); border-radius: calc(var(--icon-size) * 0.35);
  transition: all 0.3s ease;
}

.logo-icon-container.has-shadow {
  background: var(--bg-glass);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--border-glass);
  box-shadow: var(--shadow-glass);
}

/* 暗黑模式下强化图标容器质感 */
html.dark .logo-icon-container.has-shadow {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.05), var(--shadow-glass);
}

.logo-text-container { padding: calc(var(--icon-size) * 0.05) calc(var(--icon-size) * 0.15); transition: all 0.3s ease; }
.logo-icon { width: var(--icon-size); height: var(--icon-size); border-radius: calc(var(--icon-size) * 0.24); overflow: hidden; flex-shrink: 0; transition: filter 0.3s ease; }
.logo-icon img { width: 100%; height: 100%; object-fit: cover; }

/* 暗黑模式下降低图标亮度，使其更融合 */
html.dark .logo-icon {
  filter: brightness(0.9) contrast(1.1);
}

.logo-text {
  display: inline-block;
  font-family: 'Futura', 'Montserrat', sans-serif;
  font-size: calc(var(--icon-size) * 0.6); font-weight: 700;
  color: var(--text-color); letter-spacing: -0.02em; line-height: 1;
  transition: all 0.3s ease;
}

/* 暗黑模式下文字增加微弱发光感 */
html.dark .logo-text {
  text-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
  background: linear-gradient(180deg, #ffffff 0%, #e8eaed 100%);
  -webkit-background-clip: text;
  background-clip: text;
}

.app-logo-wrapper.is-clickable { cursor: pointer; }
.app-logo-wrapper.is-clickable:hover { transform: translateY(-1px); }

/* 悬停效果优化 */
.app-logo-wrapper.is-clickable:hover .logo-text {
  color: var(--primary);
  text-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

html.dark .app-logo-wrapper.is-clickable:hover .logo-text {
  color: var(--primary);
  text-shadow: 0 0 12px rgba(77, 171, 255, 0.4);
  background: none; /* 悬停时取消渐变，使用纯色 */
  -webkit-background-clip: initial;
  background-clip: initial;
}

.app-logo-wrapper.is-clickable:active { transform: scale(0.98); }
</style>
