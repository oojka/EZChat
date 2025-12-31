<script setup lang="ts">
import { COMMON_EMOJIS } from '@/constants/emojis'
import { useI18n } from 'vue-i18n'
import { ref, onMounted } from 'vue'

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'select', emoji: string): void
}>()

const recentEmojis = ref<string[]>([])

onMounted(() => {
  const saved = localStorage.getItem('recent_emojis')
  if (saved) {
    recentEmojis.value = JSON.parse(saved)
  }
})

const handleSelect = (emoji: string) => {
  emit('select', emoji)

  // 更新最近使用
  const index = recentEmojis.value.indexOf(emoji)
  if (index > -1) {
    recentEmojis.value.splice(index, 1)
  }
  recentEmojis.value.unshift(emoji)
  if (recentEmojis.value.length > 10) {
    recentEmojis.value.pop()
  }
  localStorage.setItem('recent_emojis', JSON.stringify(recentEmojis.value))
}
</script>

<template>
  <div class="emoji-picker-container">
    <div class="emoji-scroll-area">
      <!-- 全部表情 -->
      <div class="emoji-section">
        <div class="section-title">{{ t('chat.all_emojis') }}</div>
        <div class="emoji-grid">
          <button
            v-for="emoji in COMMON_EMOJIS"
            :key="emoji"
            class="emoji-item"
            type="button"
            @mousedown.prevent
            @click="handleSelect(emoji)"
          >
            {{ emoji }}
          </button>
        </div>
      </div>

      <!-- 最近使用 -->
      <div v-if="recentEmojis.length > 0" class="emoji-section">
        <div class="section-title">{{ t('chat.recent') }}</div>
        <div class="emoji-grid">
          <button
            v-for="emoji in recentEmojis"
            :key="'recent-' + emoji"
            class="emoji-item"
            type="button"
            @mousedown.prevent
            @click="handleSelect(emoji)"
          >
            {{ emoji }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.emoji-picker-container {
  display: flex;
  flex-direction: column;
  width: 420px;
  height: 300px;
  background-color: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-sizing: border-box;
}

.emoji-scroll-area {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.emoji-section {
  margin-bottom: 20px;
}

.emoji-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-400);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
  padding-left: 4px;
}

/* 自定义滚动条 */
.emoji-scroll-area::-webkit-scrollbar {
  width: 5px;
}

.emoji-scroll-area::-webkit-scrollbar-thumb {
  background: var(--el-border-color-light);
  border-radius: var(--radius-round);
}

.emoji-scroll-area::-webkit-scrollbar-thumb:hover {
  background: var(--text-400);
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 2px;
}

.emoji-item {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  cursor: pointer;
  border-radius: var(--radius-sm);
  border: none;
  background: transparent;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 0;
}

.emoji-item:hover {
  background-color: var(--bg-page);
  transform: scale(1.2) translateY(-2px);
  z-index: 1;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.emoji-item:active {
  transform: scale(0.95);
}

/* 暗黑模式微调 */
html.dark .emoji-item:hover {
  background-color: rgba(255, 255, 255, 0.05);
}
</style>
