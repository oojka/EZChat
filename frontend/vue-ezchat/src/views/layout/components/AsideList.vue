<script setup lang="ts">
import {nextTick, ref, watch} from 'vue'
import {Link, Plus} from '@element-plus/icons-vue'
import ChatItem from '@/views/layout/components/ChatItem.vue'
import CreateChatDialog from '@/components/dialogs/CreateChatDialog.vue'
import JoinChatDialog from '@/components/dialogs/JoinChatDialog.vue'
import {useRoomStore} from '@/stores/roomStore.ts'
import {useAppStore} from '@/stores/appStore.ts'
import {storeToRefs} from 'pinia'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import { useJoinChat } from '@/hooks/useJoinChat'

/** 
 * 国际化
 */
const { t } = useI18n()

/**
 * 侧边栏聊天列表组件
 * 
 * 功能：
 * 1. 显示用户加入的聊天室列表
 * 2. 提供创建新聊天室和加入现有聊天室的入口
 * 3. 自动滚动到当前激活的聊天室
 * 4. 处理聊天室选择导航
 */

// =========================
// Store 依赖
// =========================

/**
 * 聊天室 Store
 * 用于获取和管理聊天室列表数据
 */
const roomStore = useRoomStore()
const { roomList, currentRoomCode, isRoomListLoading, createChatDialogVisible, joinChatDialogVisible } = storeToRefs(roomStore)


/**
 * 路由实例
 * 用于页面导航
 */
const router = useRouter()

// =========================
// Hook 依赖
// =========================

/**
 * 加入聊天室 Hook
 * 提供加入聊天室相关功能，包括对话框控制
 */
const { joinDialogVisible } = useJoinChat()

// =========================
// 组件状态
// =========================

/**
 * 滚动容器引用
 * 用于实现自动滚动到当前激活聊天室的功能
 */
const listContentRef = ref<HTMLElement | null>(null)

/**
 * 处理聊天室选择
 * 
 * 业务逻辑：
 * 1. 用户点击聊天室项时触发
 * 2. 导航到对应的聊天页面
 * 
 * @param chatCode 聊天室代码
 */
const handleSelectChat = (chatCode: string) => {
  router.push(`/chat/${chatCode}`)
}

/**
 * 滚动到当前激活的房间
 * 
 * 业务目的：
 * - 当 currentRoomCode 变化时（例如创建房间后导航），自动滚动到目标房间
 * - 使目标房间在可视区域内居中显示，提升用户体验
 * 
 * 实现细节：
 * 1. 边界检查：避免在加载期间执行滚动
 * 2. 等待 DOM 更新：确保目标元素已渲染
 * 3. 使用 CSS 选择器查找目标元素
 * 4. 平滑滚动到目标位置
 */
const scrollToCurrentRoom = async () => {
  // 边界检查：如果列表正在加载，则不执行滚动
  if (isRoomListLoading.value) return
  
  // 等待 DOM 更新完成，确保聊天室项已渲染
  await nextTick()
  
  // 检查滚动容器是否已渲染
  if (!listContentRef.value) return
  
  // 检查是否有当前房间代码
  if (!currentRoomCode.value) return
  
  // 查找目标房间元素
  // 使用 data-chat-code 属性选择器，确保准确找到目标元素
  const targetElement = listContentRef.value.querySelector(
    `[data-chat-code="${currentRoomCode.value}"]`
  ) as HTMLElement | null
  
  // 如果找到目标元素，滚动到该位置
  if (targetElement) {
    targetElement.scrollIntoView({
      behavior: 'smooth',  // 平滑滚动效果
      block: 'center',     // 垂直方向居中
      inline: 'nearest',   // 水平方向最近
    })
  }
}

/**
 * 监听当前房间代码变化
 * 
 * 业务逻辑：
 * 1. 当用户切换聊天室或创建新聊天室时，currentRoomCode 会变化
 * 2. 变化时自动滚动到对应的聊天室项
 * 3. 避免初始化时的无效滚动（newVal !== oldVal 检查）
 */
watch(
  () => currentRoomCode.value,
  (newVal, oldVal) => {
    // 只在值真正变化时滚动（避免初始化时的无效滚动）
    if (newVal && newVal !== oldVal) {
      scrollToCurrentRoom()
    }
  }
)

// =========================
// 注意事项
// =========================

/**
 * 房间列表加载说明：
 * 房间列表由 appStore.initializeApp(refresh) 统一触发；
 * 此处不再重复请求，避免刷新期间并发打满服务器。
 * 
 * 表单清空逻辑：
 * 在 useCreateChat 的 watch 中，当 createRoomVisible 变为 true 时自动清空表单。
 */
</script>

<template>
  <!-- 侧边栏聊天列表容器 -->
  <div class="aside-list-container">
    
    <!-- 头部区域：标题和操作按钮 -->
    <div class="aside-header">
      <h3 class="aside-title">{{ t('chat.chat_list') }}</h3>
      <div class="action-group">
        <!-- 加入聊天室按钮：显示加入对话框 -->
        <el-button class="header-action-btn" :icon="Link" @click="joinChatDialogVisible = true" />
        <!-- 创建聊天室按钮：显示创建对话框 -->
        <el-button class="header-action-btn" :icon="Plus" @click="createChatDialogVisible = true" />
      </div>
    </div>

    <!-- 聊天列表内容区域 -->
    <div class="list-content" ref="listContentRef">
      
      <!-- 加载状态：显示骨架屏 -->
      <!-- 设计考虑：优先展示 Skeleton，避免"先出现 1 条，再补齐"的闪烁现象 -->
      <div v-if="isRoomListLoading && (!roomList || roomList.length === 0)" class="chat-skeleton">
        <el-skeleton animated :rows="6" />
      </div>
      
      <!-- 有聊天室数据：显示聊天室列表 -->
      <div v-if="roomList && roomList.length > 0" class="chat-items">
        <ChatItem
          v-for="chat in roomList"
          :key="chat.chatCode"
          :chat="chat"
          :is-active="chat.chatCode === currentRoomCode"
          @click="handleSelectChat(chat.chatCode)"
        />
      </div>
      
      <!-- 无聊天室数据：显示空状态 -->
      <div v-else class="empty-state">
        <el-empty :description="t('aside.no_chats')" :image-size="80" />
      </div>
    </div>

    <!-- 对话框组件 -->
    <!-- 创建聊天室对话框：通过 createChatDialogVisible 控制显示 -->
    <CreateChatDialog v-if="createChatDialogVisible" />
    <!-- 加入聊天室对话框：通过 joinChatDialogVisible 控制显示 -->
    <JoinChatDialog v-if="joinChatDialogVisible" />
  </div>
</template>

<style scoped>
/**
 * 侧边栏聊天列表样式
 * 设计原则：简洁、清晰、符合整体设计语言
 */

/* 主容器样式 */
.aside-list-container {
  height: 100%; 
  display: flex; 
  flex-direction: column;
  background-color: var(--bg-aside); /* 使用主题变量 */
  border-right: 1px solid var(--el-border-color-light);
  transition: background-color 0.3s ease; /* 平滑过渡效果 */
}

/* 头部区域样式 */
.aside-header { 
  padding: 14px 20px; 
  display: flex; 
  align-items: center; 
  justify-content: space-between; 
  border-bottom: 1px solid var(--el-border-color-light); 
}

/* 标题样式 */
.aside-title { 
  font-size: 16px; 
  font-weight: 800; 
  color: var(--text-900); 
  margin: 0; 
}

/* 操作按钮组样式 */
.action-group { 
  display: flex; 
  gap: 8px; /* 按钮间距 */
}

/* 头部操作按钮样式 */
.header-action-btn { 
  width: 32px; 
  height: 32px; 
  padding: 0; 
  background-color: var(--bg-glass); /* 毛玻璃效果背景 */
  border: none; 
  border-radius: 8px; 
  color: var(--text-500); 
  transition: all 0.2s; /* 平滑悬停效果 */
}

/* 按钮悬停效果 */
.header-action-btn:hover { 
  background-color: var(--primary); 
  color: #fff; 
}

/* 列表内容区域样式 */
.list-content { 
  flex: 1; /* 占据剩余空间 */
  overflow-y: auto; /* 垂直滚动 */
}

/* 空状态样式 */
.empty-state { 
  padding-top: 60px; /* 顶部内边距，视觉居中 */
}
</style>
