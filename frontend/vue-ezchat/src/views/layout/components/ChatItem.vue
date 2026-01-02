<script setup lang="ts">
import {onMounted, onUnmounted, ref, watch} from 'vue'
import dayjs from 'dayjs'
import isToday from 'dayjs/plugin/isToday'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ja'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/zh-tw'
import 'dayjs/locale/ko'
import type {ChatRoom} from '@/type'
import {useMessageStore} from '@/stores/messageStore.ts'
import {useI18n} from 'vue-i18n'
import SmartAvatar from '@/components/SmartAvatar.vue'

const { t, locale } = useI18n()
const messageStore = useMessageStore()

dayjs.extend(isToday)
dayjs.extend(relativeTime)

const dayjsLocaleMap: Record<string, string> = {
  'ja': 'ja', 'zh': 'zh-cn', 'zh-tw': 'zh-tw', 'en': 'en', 'ko': 'ko'
}

const props = withDefaults(
  defineProps<{
    chat: ChatRoom
    isActive?: boolean
  }>(),
  { isActive: false },
)

const formattedTime = ref('')

const formatDisplayTime = (timeStr?: string): string => {
  if (!timeStr) return ''
  const target = dayjs(timeStr).locale(dayjsLocaleMap[locale.value] || 'en')
  if (!target.isValid()) return ''
  const now = dayjs()
  const diffMinute = now.diff(target, 'minute')
  if (diffMinute < 360 && diffMinute >= 0) return target.fromNow()
  if (diffMinute < 0) return t('aside.just_now')
  if (target.isToday()) return target.format('HH:mm')
  if (target.year() === now.year()) return target.format('MM/DD')
  return target.format('YY/MM/DD')
}

watch([() => props.chat.lastActiveAt, locale], () => {
  formattedTime.value = formatDisplayTime(props.chat.lastActiveAt)
}, { immediate: true })

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  timer = setInterval(() => { formattedTime.value = formatDisplayTime(props.chat.lastActiveAt) }, 60000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="chat-item" :class="{ 'is-active': isActive }" :data-chat-code="chat.chatCode">
    <div class="avatar-wrapper">
      <SmartAvatar
        class="avatar"
        :size="50"
        shape="square"
        :thumb-url="chat.avatar?.blobThumbUrl || chat.avatar?.objectThumbUrl || ''"
        :url="chat.avatar?.blobUrl || chat.avatar?.objectUrl || ''"
        :text="chat.chatName"
      />
    </div>

    <div class="info-container">
      <div class="row1">
        <div class="name">{{ chat.chatName }}</div>
        <div class="last-active">{{ formattedTime }}</div>
      </div>

      <div class="row2">
        <div class="last-message">{{ messageStore.formatPreviewMessage(chat.lastMessage) }}</div>
        <div class="badge-wrapper" v-if="(chat.unreadCount || 0) > 0">
          <el-badge :value="chat.unreadCount || 0" :max="99" class="unread-badge" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-item {
  display: flex; align-items: center; padding: 12px 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer; user-select: none;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background-color: transparent;
  position: relative;
}
.chat-item:last-child { border-bottom: none; }

/* 悬停态：使用半透明叠加，确保在任何背景下都可见 */
.chat-item:hover {
  background-color: rgba(148, 163, 184, 0.08);
}
html.dark .chat-item:hover {
  background-color: rgba(255, 255, 255, 0.04);
}

/* 激活态：深度优化 */
.chat-item.is-active {
  background-color: var(--primary-light);
}

/* 激活态下的左侧指示条 */
.chat-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 15%;
  bottom: 15%;
  width: 4px;
  background-color: var(--primary);
  border-radius: 0 var(--radius-ss) var(--radius-ss) 0;
  box-shadow: 0 0 10px var(--primary);
}

.avatar-wrapper { margin-right: 12px; flex-shrink: 0; }
.avatar {
  border-radius: var(--radius-base);
  border: 1px solid var(--el-border-color-light);
  transition: transform 0.3s ease;
}
.chat-item:hover .avatar { transform: scale(1.05); }

.info-container { flex: 1; overflow: hidden; display: flex; flex-direction: column; justify-content: center; }
.row1, .row2 { display: flex; justify-content: space-between; align-items: center; }
.row2 { margin-top: 4px; }

.name {
  font-size: 15px; font-weight: 700; color: var(--text-900);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  transition: color 0.3s ease;
}
.chat-item.is-active .name { color: var(--primary); }

.last-active { font-size: 11px; color: var(--text-500); white-space: nowrap; margin-left: 10px; }
.last-message { font-size: 13px; color: var(--text-400); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px; }

.badge-wrapper { flex-shrink: 0; display: flex; align-items: center; }
.unread-badge :deep(.el-badge__content) { transform: none; position: static; border: none; font-weight: 800; }
</style>
