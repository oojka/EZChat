<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import MessageArea from '@/views/chat/layout/MessageArea.vue'
import InputArea from '@/views/chat/layout/InputArea.vue'
import RightAside from '@/views/chat/layout/RightAside.vue'
import ChatHeader from '@/views/chat/layout/ChatHeader.vue'
import ChatSkeleton from '@/views/chat/components/ChatSkeleton.vue'
import {useMessageStore} from '@/stores/messageStore.ts'
import {storeToRefs} from 'pinia'
import {ArrowLeft, ArrowRight} from '@element-plus/icons-vue'

const messageStore = useMessageStore()
const { chatViewIsLoading } = storeToRefs(messageStore)

const isCollapse = ref(false)
const toggleSidePanel = () => { isCollapse.value = !isCollapse.value }

const inputPanelHeight = ref(220)
const isResizing = ref(false)

const startResizing = (e: MouseEvent) => {
  isResizing.value = true
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', stopResizing)
  document.body.style.cursor = 'ns-resize'
  document.body.style.userSelect = 'none'
}

const handleMouseMove = (e: MouseEvent) => {
  if (!isResizing.value) return
  const newHeight = window.innerHeight - e.clientY
  const availableHeight = window.innerHeight - 60
  const maxHeight = Math.max(availableHeight * 0.5, 220)
  if (newHeight >= 220 && newHeight <= maxHeight) inputPanelHeight.value = newHeight
  else if (newHeight < 220) inputPanelHeight.value = 220
  else if (newHeight > maxHeight) inputPanelHeight.value = maxHeight
}

const stopResizing = () => {
  isResizing.value = false
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', stopResizing)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
}

onMounted(() => {
  const initialHeight = (window.innerHeight - 60) * 0.3
  inputPanelHeight.value = Math.max(initialHeight, 220)
})
onUnmounted(() => stopResizing())
</script>

<template>
  <div class="chat-view-wrapper">
    <el-container class="chat-container">
      <el-header class="chat-area-header" height="60px">
        <ChatHeader />
      </el-header>

      <el-container class="chat-main-layout">
        <el-main class="chat-left-panel">
          <div class="message-panel">
            <ChatSkeleton v-if="chatViewIsLoading" />
            <MessageArea v-else />
          </div>

          <div class="horizontal-resizer" :class="{ 'is-resizing': isResizing }" @mousedown.prevent="startResizing"></div>

          <div class="input-panel" :style="{ height: inputPanelHeight + 'px' }">
            <InputArea />
          </div>
        </el-main>

        <el-aside class="chat-right-panel" :class="{ 'is-hidden': isCollapse }" width="300px">
          <div class="side-resizer-trigger" @click.stop="toggleSidePanel">
            <div class="trigger-icon-wrapper"><el-icon><ArrowLeft v-if="isCollapse" /><ArrowRight v-else /></el-icon></div>
          </div>
          <RightAside />
        </el-aside>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.chat-view-wrapper { height: 100%; width: 100%; display: flex; flex-direction: column; }
.chat-container { height: 100%; background-color: var(--bg-card); overflow: hidden; transition: background-color 0.3s ease; }

.chat-area-header {
  padding: 0;
  background-color: var(--primary-light);
  border-bottom: 1px solid var(--el-border-color-light);
  z-index: 20;
  transition: all 0.3s ease;
}

.chat-main-layout { overflow: hidden; height: calc(100% - 60px); }
.chat-left-panel { padding: 0; display: flex; flex-direction: column; background-color: var(--bg-card); }
.message-panel { flex: 1; background-color: var(--bg-page); overflow: hidden; position: relative; transition: background-color 0.3s ease; }

.horizontal-resizer { height: 6px; cursor: ns-resize; position: relative; z-index: 50; flex-shrink: 0; display: flex; align-items: center; background-color: transparent; }
.horizontal-resizer::after { content: ''; width: 100%; height: 1px; background-color: var(--el-border-color-light); transition: all 0.2s ease; }
.horizontal-resizer:hover::after, .horizontal-resizer.is-resizing::after { height: 2px; background-color: var(--text-400); }

.input-panel { background-color: var(--bg-card); min-height: 220px; overflow: hidden; flex-shrink: 0; transition: background-color 0.3s ease; }

.chat-right-panel {
  position: relative;
  background-color: var(--bg-card);
  border-left: 1px solid var(--el-border-color-light);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: visible;
}
.chat-right-panel.is-hidden { width: 0 !important; border-left-color: transparent; }

.side-resizer-trigger { position: absolute; left: -14px; top: 50%; transform: translateY(-50%); width: 28px; height: 120px; cursor: pointer; z-index: 100; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity 0.2s ease, left 0.3s ease; }
.chat-right-panel:hover .side-resizer-trigger, .side-resizer-trigger:hover { opacity: 1; }
.trigger-icon-wrapper { width: 24px; height: 56px; background-color: var(--primary); color: #fff; border-radius: var(--radius-sm); display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); font-size: 16px; }
:deep(.el-main) { overflow: hidden; }
</style>
