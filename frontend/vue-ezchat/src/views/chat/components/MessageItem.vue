<script setup lang="ts">
import {computed, ref, watchEffect} from 'vue'
import {Loading, Picture as IconPicture, WarningFilled} from '@element-plus/icons-vue'
import { ElImageViewer } from 'element-plus'
import SmartAvatar from '@/components/SmartAvatar.vue'
import type {ChatRoom, Message} from '@/type'
import {useUserStore} from '@/stores/userStore.ts'
import {useImageStore} from '@/stores/imageStore'
import {storeToRefs} from 'pinia'

const props = defineProps<{
  msg: Message
  currentChat: ChatRoom | undefined
}>()

const userStore = useUserStore()
const { loginUserInfo } = storeToRefs(userStore)
const imageStore = useImageStore()

const isMe = computed(() => props.msg.sender === loginUserInfo.value?.uid)

const senderInfo = computed(() => {
  if (!props.currentChat || !props.currentChat.chatMembers) return null
  return props.currentChat.chatMembers.find((m) => m.uid === props.msg.sender)
})

const avatarThumbUrl = computed(() =>
  senderInfo.value?.avatar?.blobThumbUrl
  || senderInfo.value?.avatar?.objectThumbUrl
  || ''
)
const avatarUrl = computed(() =>
  senderInfo.value?.avatar?.blobUrl
  || senderInfo.value?.avatar?.objectUrl
  || ''
)

// 严格的 Emoji 正则表达式：只匹配真正的 Emoji，排除 CJK 字符、假名和标点符号
// 匹配范围：
// - \u00a9 (©), \u00ae (®): 版权和注册商标符号
// - \ud83c[\udf00-\udfff]: Emoji 标志和符号 (U+1F300-U+1F3FF)
// - \ud83d[\udc00-\udfff]: 表情符号和手势 (U+1F400-U+1F4FF, U+1F500-U+1F9FF)
// - \ud83e[\udd00-\uddff]: 补充表情符号 (U+1F900-U+1F9FF)
// 注意：排除 \u2000-\u3300 范围（包含假名、CJK 标点等）
// 排除 \p{Extended_Pictographic}（太宽泛，会匹配非 Emoji 字符）
const emojiRegex = /(\u00a9|\u00ae|\ud83c[\udf00-\udfff]|\ud83d[\udc00-\udfff]|\ud83e[\udd00-\uddff])/gu

const renderedText = computed(() => {
  if (!props.msg.text) return ''
  const escaped = props.msg.text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
  return escaped.replace(emojiRegex, '<span class="inline-emoji">$1</span>')
})

// 大图预览（原图 blob 按需拉取）
const viewerVisible = ref(false)
const viewerUrls = ref<string[]>([])
const viewerIndex = ref(0)

const openViewer = async (idx: number) => {
  const images = props.msg.images || []
  if (images.length === 0) return
  viewerIndex.value = idx
  // 1) 按需拉取原图 blob（并刷新预签名 URL）
  const urls = await Promise.all(images.map((img) => imageStore.ensureOriginalBlobUrl(img)))
  // 2) 兜底：确保 url-list 中没有空值
  viewerUrls.value = urls
    .map((u, i) => u || images[i]?.objectUrl || images[i]?.objectThumbUrl)
    .filter(Boolean) as string[]
  viewerVisible.value = true
}
</script>

<template>
  <li class="message-row" :class="{ 'is-me': isMe }">
    <SmartAvatar
      class="user-avatar"
      :size="38"
      shape="square"
      :thumb-url="avatarThumbUrl"
      :url="avatarUrl"
      :text="senderInfo?.nickname || '?'"
    />

    <div class="content-wrapper">
      <div class="nickname" v-if="!isMe">{{ senderInfo?.nickname || 'Unknown' }}</div>

      <div class="bubble-wrapper">
        <!-- 自己消息的发送状态：仅在 sending/error 时渲染，避免空占位导致“图片与头像间距异常” -->
        <div v-if="isMe && (msg.status === 'sending' || msg.status === 'error')" class="status-indicator">
          <el-icon v-if="msg.status === 'sending'" class="is-loading status-icon"><Loading /></el-icon>
          <el-icon v-if="msg.status === 'error'" class="status-icon error"><WarningFilled /></el-icon>
        </div>

        <div class="message-stack">
          <!-- 文字气泡：type=0(Text) 或 type=2(Mixed) -->
          <div v-if="msg.type !== 1 && msg.text" class="message-text-bubble">
            <span class="message-text" v-html="renderedText"></span>
          </div>

          <!-- 图片组：type=1(Image) 或 type=2(Mixed) -->
          <div
            v-if="msg.type !== 0 && msg.images?.length"
            class="message-img-container"
            :class="{ 'multi-imgs': msg.images.length > 1 }"
          >
            <el-image
              v-for="(img, idx) in msg.images"
              :key="idx"
              :src="img.blobThumbUrl || img.blobUrl || img.objectThumbUrl"
              class="img-item"
              fit="contain"
              loading="lazy"
              @click="openViewer(idx)"
            >
              <template #placeholder><div class="img-placeholder"><el-icon class="is-loading"><Loading /></el-icon></div></template>
              <template #error><div class="img-error"><el-icon><IconPicture /></el-icon></div></template>
            </el-image>
          </div>
        </div>
      </div>

      <div class="message-timeStamp" :class="{ 'is-me': isMe }">{{ msg.createTime?.replace('T', ' ').slice(0, 16) }}</div>
    </div>
  </li>

  <!-- 自定义大图预览：等原图准备好后再打开，避免预签名过期/重复下载 -->
  <el-image-viewer
    v-if="viewerVisible"
    :url-list="viewerUrls"
    :initial-index="viewerIndex"
    @close="viewerVisible = false"
  />
</template>

<style scoped>
.message-row { display: flex; align-items: flex-start; gap: 12px; width: 100%; }
.message-row.is-me { flex-direction: row-reverse; }

.user-avatar { flex-shrink: 0; border-radius: var(--radius-base); border: 1px solid var(--el-border-color-light); transition: all 0.3s ease; }
html.dark .user-avatar { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4); border-color: rgba(255, 255, 255, 0.1); }

.content-wrapper { display: flex; flex-direction: column; gap: 4px; max-width: 75%; align-items: flex-start; }
.message-row.is-me .content-wrapper { align-items: flex-end; }
.nickname { font-size: 11px; color: var(--text-500); margin-bottom: 2px; font-weight: 600; }

.bubble-wrapper { display: flex; align-items: center; gap: 8px; max-width: 100%; }
/* 自己消息：气泡与图片靠右贴近头像；状态图标在最右侧（更贴近头像） */
.message-row.is-me .bubble-wrapper { flex-direction: row-reverse; justify-content: flex-start; }

.message-stack { display: flex; flex-direction: column; gap: 3px; max-width: 100%; }
.message-row.is-me .message-stack { align-items: flex-end; }

/* --- 文字气泡基础样式 --- */
.message-text-bubble {
  padding: 8px 14px;
  border-radius: var(--radius-md);
  font-size: 15px;
  line-height: 1.6;
  word-break: break-word;
  position: relative;
  width: fit-content;
  transition: all 0.3s var(--ease-out-expo);
  /* 核心修复：统一边框宽度，防止切换时抖动 */
  border: 1px solid transparent;
  /* Web Font 方案：使用 Noto Sans 确保中日文混排时高度一致 */
  font-family: "Noto Sans JP", "Noto Sans SC", sans-serif !important;
  /* 优化文本渲染 */
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-feature-settings: "palt" 1;
  -webkit-font-feature-settings: "palt" 1;
  font-optical-sizing: none;
}

/* 别人发的消息气泡 */
.message-row:not(.is-me) .message-text-bubble {
  background-color: var(--bg-card);
  color: var(--text-900);
  border-top-left-radius: 2px;
  border-color: var(--el-border-color-light);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}
html.dark .message-row:not(.is-me) .message-text-bubble {
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.02), 0 4px 12px rgba(0, 0, 0, 0.2);
}

/* 自己发的消息气泡 */
.message-row.is-me .message-text-bubble {
  background: linear-gradient(135deg, var(--primary) 0%, #3393ff 100%);
  color: #ffffff;
  border-top-right-radius: 2px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

/* 暗黑模式：自己消息气泡适配 */
html.dark .message-row.is-me .message-text-bubble {
  background: rgba(59, 130, 246, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #ffffff;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.1);
  /* 核心修复：保持 1px 边框，仅改变颜色 */
  border-color: rgba(255, 255, 255, 0.05);
}

.message-text {
  white-space: pre-wrap;
  display: inline-block;
  /* Web Font 方案：使用 Noto Sans 确保中日文混排时高度一致 */
  font-family: "Noto Sans JP", "Noto Sans SC", sans-serif !important;
  line-height: 1.5;
  /* 优化文本渲染 */
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  font-feature-settings: "palt" 1;
  -webkit-font-feature-settings: "palt" 1;
  font-optical-sizing: none;
}
:deep(.inline-emoji) { font-size: 1.4em; vertical-align: -0.15em; line-height: 1; display: inline-block; margin: 0 1px; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1)); }

/* 图片消息：容器负责宽度约束（<= 80%），子项填满容器，避免百分比嵌套导致异常占位 */
.message-img-container {
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  max-width: 80%;
  align-items: flex-start;
}

/* 自己消息：图片容器靠右（贴近头像），避免出现“头像与图片之间多余空隙” */
.message-row.is-me .message-img-container {
  align-items: flex-end;
}

.message-img-container.multi-imgs {
  display: inline-flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: flex-start;
}

.img-item {
  width: 100%;
  max-width: 100%;
  border-radius: var(--radius-base);
  cursor: pointer;
  display: block;
  background-color: var(--bg-page);
  transition: all 0.3s ease;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.message-img-container.multi-imgs .img-item {
  width: calc(50% - 3px);
  max-width: calc(50% - 3px);
}

/* 覆盖 Element Plus 默认 inner：让高度随图片比例自适应 */
::deep(.img-item .el-image__inner) {
  width: 100%;
  height: auto !important;
  display: block;
}

::deep(.img-item .el-image__wrapper) {
  width: 100%;
  height: auto !important;
}

html.dark .img-item {
  filter: brightness(0.85) contrast(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.1);
}
html.dark .img-item:hover { filter: brightness(1) contrast(1); transform: scale(1.01); }

.img-placeholder, .img-error {
  width: 100%;
  min-height: 120px;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--el-fill-color-blank);
  color: var(--text-400);
  font-size: 20px;
}

.status-indicator { display: flex; align-items: center; justify-content: center; width: 16px; height: 16px; }
.status-icon { font-size: 14px; color: var(--text-400); }
.status-icon.error { color: var(--el-color-danger); cursor: pointer; }

.message-timeStamp { font-size: 10px; color: var(--text-400); margin-top: 4px; letter-spacing: 0.5px; transition: color 0.3s ease; }
</style>
