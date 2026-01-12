<script setup lang="ts">
/**
 * 聊天页面组件
 *
 * 核心功能：
 * - 消息列表展示与实时接收
 * - 消息发送（文本/图片）
 * - 成员列表管理
 * - 输入区域拖拽调整高度（桌面端）
 *
 * 路由参数：
 * - chatCode: 8 位房间号
 *
 * 布局结构：
 * - ChatHeader: 顶部房间信息栏
 * - MessageArea: 消息列表区域（支持无限滚动）
 * - InputArea: 底部输入区域
 * - RightAside: 右侧成员列表（桌面端固定/移动端抽屉）
 *
 * 依赖：
 * - messageStore: 消息状态管理
 * - useIsMobile: 设备类型检测
 * - useKeyboardVisible: 移动端键盘状态检测
 */
import { onMounted, onUnmounted, ref, computed } from 'vue'
import MessageArea from '@/views/chat/sections/MessageArea.vue'
import InputArea from '@/views/chat/sections/InputArea.vue'
import RightAside from '@/views/chat/sections/RightAside.vue'
import ChatHeader from '@/views/chat/sections/ChatHeader.vue'
import MessageSkeleton from '@/views/chat/components/MessageSkeleton.vue'
import { useMessageStore } from '@/stores/messageStore.ts'
import { storeToRefs } from 'pinia'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useIsMobile } from '@/composables/useIsMobile'
import { useKeyboardVisible } from '@/composables/useKeyboardVisible'

const messageStore = useMessageStore()
const { chatViewIsLoading } = storeToRefs(messageStore)
const { isMobile } = useIsMobile()
const { isKeyboardVisible } = useKeyboardVisible()

const isCollapse = ref(false)
const toggleSidePanel = () => { isCollapse.value = !isCollapse.value }

/** 移动端成员列表 Drawer 可见状态 */
const memberDrawerVisible = ref(false)

/** 桌面端输入区拖拽 resize 相关 */
const inputPanelHeight = ref(220)
const isResizing = ref(false)

/** 移动端禁用拖拽 resize */
const startResizing = (_e: MouseEvent) => {
  if (isMobile.value) return
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

/** 移动端输入区使用自增高样式，不设置固定高度 */
const inputPanelStyle = computed(() => {
  if (isMobile.value) return {}
  return { height: inputPanelHeight.value + 'px' }
})

onMounted(() => {
  if (!isMobile.value) {
    const initialHeight = (window.innerHeight - 60) * 0.3
    inputPanelHeight.value = Math.max(initialHeight, 220)
  }
})
onUnmounted(() => stopResizing())
</script>

<template>
  <div class="chat-view-wrapper" :class="{ 'is-mobile': isMobile, 'keyboard-visible': isKeyboardVisible }">
    <el-container class="chat-container">
      <el-header class="chat-area-header" height="60px">
        <ChatHeader
          :is-mobile="isMobile"
          @open-member-drawer="memberDrawerVisible = true"
        />
      </el-header>

      <el-container class="chat-main-layout">
        <el-main class="chat-left-panel">
          <div class="message-panel">
            <Transition name="chat-fade" mode="in-out">
              <MessageSkeleton v-if="chatViewIsLoading" />
              <MessageArea v-else />
            </Transition>
          </div>

          <!-- 桌面端：拖拽分隔条 -->
          <div
            v-if="!isMobile"
            class="horizontal-resizer"
            :class="{ 'is-resizing': isResizing }"
            @mousedown.prevent="startResizing"
          ></div>

          <div class="input-panel" :class="{ 'mobile-input-panel': isMobile }" :style="inputPanelStyle">
            <InputArea :is-mobile="isMobile" />
          </div>
        </el-main>

        <!-- 桌面端：右侧固定成员列表 -->
        <el-aside v-if="!isMobile" class="chat-right-panel" :class="{ 'is-hidden': isCollapse }" width="300px">
          <div class="side-resizer-trigger" @click.stop="toggleSidePanel">
            <div class="trigger-icon-wrapper"><el-icon><ArrowLeft v-if="isCollapse" /><ArrowRight v-else /></el-icon></div>
          </div>
          <RightAside />
        </el-aside>
      </el-container>
    </el-container>

    <!-- 移动端：底部抽屉成员列表 -->
    <RightAside
      v-if="isMobile"
      :is-mobile="true"
      v-model:drawer-visible="memberDrawerVisible"
    />
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
.message-panel { flex: 1; background-color: var(--bg-page); overflow: hidden; position: relative; transition: background-color 0.3s ease; min-height: 0; }
.chat-fade-enter-active, .chat-fade-leave-active { transition: opacity 0.12s ease; }
.chat-fade-enter-from, .chat-fade-leave-to { opacity: 0; }

.horizontal-resizer { height: 6px; cursor: ns-resize; position: relative; z-index: 50; flex-shrink: 0; display: flex; align-items: center; background-color: transparent; }
.horizontal-resizer::after { content: ''; width: 100%; height: 1px; background-color: var(--el-border-color-light); transition: all 0.2s ease; }
.horizontal-resizer:hover::after, .horizontal-resizer.is-resizing::after { height: 2px; background-color: var(--text-400); }

.input-panel { background-color: var(--bg-card); min-height: 220px; overflow: hidden; flex-shrink: 0; transition: background-color 0.3s ease; }

/* 移动端输入区：自增高 + 最大高度限制 */
.mobile-input-panel {
  min-height: auto;
  max-height: 50vh;
  height: auto;
}

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

/* 移动端整体样式调整 */
.chat-view-wrapper.is-mobile .chat-area-header {
  height: 56px;
}
.chat-view-wrapper.is-mobile .chat-main-layout {
  height: calc(100% - 56px);
}

/* 移动端：为 Tabbar 预留底部空间 */
.chat-view-wrapper.is-mobile .chat-left-panel {
  padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom, 0px));
  transition: padding-bottom 0.3s ease;
  box-sizing: border-box;
}

/* 键盘显示时移除底部间距 */
.chat-view-wrapper.is-mobile.keyboard-visible .chat-left-panel {
  padding-bottom: 0;
}

/* 移动端：输入区底部不需要额外 safe-area（已由父容器处理） */
.chat-view-wrapper.is-mobile .mobile-input-panel {
  padding-bottom: 0;
}
</style>
