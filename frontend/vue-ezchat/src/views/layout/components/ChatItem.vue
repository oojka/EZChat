<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import isToday from 'dayjs/plugin/isToday'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ja'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/zh-tw'
import 'dayjs/locale/ko'
import type { ChatRoom } from '@/type'
import { useI18n } from 'vue-i18n'
import Avatar from '@/components/Avatar.vue'
import { useMessageStore } from '@/stores/messageStore.ts'

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

/**
 * 格式化时间显示
 * 使用 ref + interval 实现定时更新（相对时间如 "5分钟前" 需要刷新）
 */
const formattedTime = ref(formatDisplayTime(props.chat.lastActiveAt))

/**
 * 预览消息文本
 * 使用 computed 自动追踪 lastMessage 变化，解决懒加载后不更新的问题
 */
const previewMessage = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-unused-expressions
  locale.value // 确保语言切换时触发更新
  return messageStore.formatPreviewMessage(props.chat.lastMessage)
})

/**
 * 监听时间变化和语言切换，更新时间显示
 */
watch([() => props.chat.lastActiveAt, locale], () => {
  formattedTime.value = formatDisplayTime(props.chat.lastActiveAt)
})

let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  timer = setInterval(() => {
    formattedTime.value = formatDisplayTime(props.chat.lastActiveAt)
  }, 60000)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="chat-item" :class="{ 'is-active': isActive }" :data-chat-code="chat.chatCode">
    <div class="avatar-wrapper">
      <Avatar class="avatar" :size="50" shape="square" :image="chat.avatar" :text="chat.chatName" />
    </div>

    <div class="info-container">
      <div class="row1">
        <div class="name">{{ chat.chatName }}</div>
        <div class="last-active">{{ formattedTime }}</div>
      </div>

      <div class="row2">
        <div class="last-message">{{ previewMessage || t('chat.new_message') }}</div>
        <div class="badge-wrapper" v-if="(chat.unreadCount || 0) > 0">
          <el-badge :value="chat.unreadCount || 0" :max="99" class="unread-badge" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background-color: transparent;
  position: relative;
}

.chat-item:last-child {
  border-bottom: none;
}

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

.avatar-wrapper {
  margin-right: 12px;
  flex-shrink: 0;
}

.avatar {
  border: 1px solid var(--el-border-color-light);
  transition: transform 0.3s ease;
}

.chat-item:hover .avatar {
  transform: scale(1.05);
}

.info-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.row1,
.row2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.row2 {
  margin-top: 4px;
}

.name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.chat-item.is-active .name {
  color: var(--primary);
}

.last-active {
  font-size: 11px;
  color: var(--text-500);
  white-space: nowrap;
  margin-left: 10px;
}

.last-message {
  font-size: 13px;
  color: var(--text-400);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-right: 8px;
}

.badge-wrapper {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.unread-badge :deep(.el-badge__content) {
  transform: none;
  position: static;
  border: none;
  font-weight: 800;
}
</style>
