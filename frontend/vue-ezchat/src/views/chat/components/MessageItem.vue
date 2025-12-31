<script setup lang="ts">
import {computed} from 'vue'
import {Loading, Picture as IconPicture, WarningFilled} from '@element-plus/icons-vue'
import type {ChatRoom, Message} from '@/type'
import {useUserStore} from '@/stores/userStore.ts'
import {storeToRefs} from 'pinia'

const props = defineProps<{
  msg: Message
  currentChat: ChatRoom | undefined
}>()

const userStore = useUserStore()
const { loginUserInfo } = storeToRefs(userStore)

const isMe = computed(() => props.msg.sender === loginUserInfo.value?.uid)

const senderInfo = computed(() => {
  if (!props.currentChat || !props.currentChat.chatMembers) return null
  return props.currentChat.chatMembers.find((m) => m.uid === props.msg.sender)
})

const emojiRegex = /(\u00a9|\u00ae|[\u2000-\u3300]|\ud83c[\ud000-\udfff]|\ud83d[\ud000-\udfff]|\ud83e[\ud000-\udfff]|\p{Extended_Pictographic})/gu

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
</script>

<template>
  <li class="message-row" :class="{ 'is-me': isMe }">
    <el-avatar :size="38" shape="square" class="user-avatar" :src="senderInfo?.avatar?.objectThumbUrl || ''">
      <template #default v-if="!senderInfo?.avatar?.objectThumbUrl">
        {{ senderInfo?.nickname?.charAt(0) || '?' }}
      </template>
    </el-avatar>

    <div class="content-wrapper">
      <div class="nickname" v-if="!isMe">{{ senderInfo?.nickname || 'Unknown' }}</div>

      <div class="bubble-wrapper">
        <div v-if="isMe" class="status-indicator">
          <el-icon v-if="msg.status === 'sending'" class="is-loading status-icon"><Loading /></el-icon>
          <el-icon v-if="msg.status === 'error'" class="status-icon error"><WarningFilled /></el-icon>
        </div>

        <div class="message-stack">
          <!-- 文字气泡 -->
          <div v-if="msg.text" class="message-text-bubble">
            <span class="message-text" v-html="renderedText"></span>
          </div>

          <!-- 图片组 -->
          <div
            v-if="msg.images?.length"
            class="message-img-container"
            :class="{ 'multi-imgs': msg.images.length > 1 }"
          >
            <el-image
              v-for="(img, idx) in msg.images"
              :key="idx"
              :src="img.blobThumbUrl || img.blobUrl || img.objectThumbUrl"
              :preview-src-list="msg.images.map((i) => i.blobUrl || i.objectUrl)"
              :initial-index="idx"
              class="img-item"
              fit="cover"
              loading="lazy"
              preview-teleported
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
</template>

<style scoped>
.message-row { display: flex; align-items: flex-start; gap: 12px; width: 100%; }
.message-row.is-me { flex-direction: row-reverse; }

.user-avatar { flex-shrink: 0; border-radius: var(--radius-base); border: 1px solid var(--el-border-color-light); transition: all 0.3s ease; }
html.dark .user-avatar { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4); border-color: rgba(255, 255, 255, 0.1); }

.content-wrapper { display: flex; flex-direction: column; gap: 4px; max-width: 75%; align-items: flex-start; }
.message-row.is-me .content-wrapper { align-items: flex-end; }
.nickname { font-size: 11px; color: var(--text-500); margin-bottom: 2px; font-weight: 600; }

.bubble-wrapper { display: flex; align-items: center; gap: 8px; width: 100%; }
.message-row.is-me .bubble-wrapper { flex-direction: row; }

.message-stack { display: flex; flex-direction: column; gap: 3px; max-width: 100%; }
.message-row.is-me .message-stack { align-items: flex-end; }

/* --- 文字气泡基础样式 --- */
.message-text-bubble {
  padding: 8px 14px; border-radius: var(--radius-md); font-size: 16px; line-height: 1.6; word-break: break-word;
  position: relative; width: fit-content;
  transition: all 0.3s var(--ease-out-expo);
  /* 核心修复：统一边框宽度，防止切换时抖动 */
  border: 1px solid transparent;
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

.message-text { white-space: pre-wrap; font-family: "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji", sans-serif; display: inline-block; }
:deep(.inline-emoji) { font-size: 1.4em; vertical-align: -0.15em; line-height: 1; display: inline-block; margin: 0 1px; filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.1)); }

.message-img-container { display: block; }
.message-img-container.multi-imgs { display: grid; grid-template-columns: repeat(auto-fit, minmax(100px, 1fr)); gap: 6px; width: 320px; }

.img-item {
  max-width: 300px; width: 100%; aspect-ratio: 1 / 1; border-radius: var(--radius-base); cursor: pointer; display: block;
  background-color: var(--bg-page); transition: all 0.3s ease;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

html.dark .img-item {
  filter: brightness(0.85) contrast(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  border-color: rgba(255, 255, 255, 0.1);
}
html.dark .img-item:hover { filter: brightness(1) contrast(1); transform: scale(1.01); }

.img-placeholder, .img-error {
  width: 100%; height: 100%; display: flex; justify-content: center; align-items: center;
  background-color: var(--el-fill-color-blank);
  color: var(--text-400); font-size: 20px;
}

.status-indicator { display: flex; align-items: center; justify-content: center; width: 16px; height: 16px; }
.status-icon { font-size: 14px; color: var(--text-400); }
.status-icon.error { color: var(--el-color-danger); cursor: pointer; }

.message-timeStamp { font-size: 10px; color: var(--text-400); margin-top: 4px; letter-spacing: 0.5px; transition: color 0.3s ease; }
</style>
