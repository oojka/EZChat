<script setup lang="ts">
/**
 * 聊天室右侧成员列表区域组件
 *
 * 功能：
 * - 展示当前房间成员列表
 * - 区分在线/离线状态
 * - 移动端使用底部抽屉展示
 *
 * Props：
 * - isMobile: 是否移动端模式
 * - drawerVisible: 抽屉可见状态（v-model）
 *
 * 特性：
 * - 与消息加载并行获取成员数据
 * - 加载中显示 AppSpinner
 * - 加载完成后自动滚动到顶部
 *
 * 依赖：
 * - roomStore: 房间状态与成员数据
 * - ChatMemberList: 成员列表子组件
 */
import ChatMemberList from '../components/ChatMemberList.vue'
import AppSpinner from '@/components/AppSpinner.vue'
import { useAppStore } from '@/stores/appStore'
import { useMessageStore } from '@/stores/messageStore'
import { useRoomStore } from '@/stores/roomStore'
import { storeToRefs } from 'pinia'
import { computed, watch, ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

/** Props */
withDefaults(defineProps<{
  isMobile?: boolean
  drawerVisible?: boolean
}>(), {
  isMobile: false,
  drawerVisible: false,
})

/** Emits - v-model:drawerVisible */
const emit = defineEmits<{
  'update:drawerVisible': [value: boolean]
}>()

const appStore = useAppStore()
const messageStore = useMessageStore()
const roomStore = useRoomStore()
const { isAppInitializing } = storeToRefs(appStore)
const { chatViewIsLoading } = storeToRefs(messageStore)
const { currentRoomCode, isCurrentRoomMembersLoading } = storeToRefs(roomStore)
const { fetchRoomMembers } = roomStore
const { t } = useI18n()

// ChatMemberList 组件的 ref，用于调用滚动方法
const memberListAreaRef = ref<InstanceType<typeof ChatMemberList> | null>(null)

/**
 * 右侧成员列表的加载策略
 *
 * 业务目的：
 * - refresh 时避免右侧区域出现"骨架屏"（由你指定改为 AppSpinner）
 * - 等 chatView 开始拉取消息/房间信息后再显示真实列表
 */
const showRightSpinner = computed(() =>
  isAppInitializing.value || chatViewIsLoading.value || isCurrentRoomMembersLoading.value
)

// refresh 初始化阶段：显示"初始化..."；普通加载阶段：显示"加载中..."
const rightSpinnerText = computed(() =>
  isAppInitializing.value ? t('common.initializing') : t('common.loading')
)

// 进入房间后：按需拉取成员列表（右侧栏用 AppSpinner 做局部遮蔽）
// 成员列表不依赖 messageList，可与消息拉取并行，缩短右侧栏可用时间
watch(
  () => [currentRoomCode.value, isAppInitializing.value] as const,
  ([code, initializing]) => {
    if (!code) return
    if (initializing) return
    fetchRoomMembers(code).then(() => {})
  },
  { immediate: true },
)

/**
 * 处理遮蔽消失后的滚动
 *
 * 业务目的：
 * - 在 loading 遮蔽（AppSpinner）淡出动画完成后，自动滚动成员列表到顶部
 * - 确保用户看到列表开头，而不是停留在之前的滚动位置
 */
const handleOverlayHidden = async () => {
  // 等待 DOM 更新完成
  await nextTick()
  // 确保成员列表已渲染（检查是否有成员数据）
  // 使用 useChatMemberList 需要从子组件获取，这里直接调用 scrollToTop
  // 添加小延迟确保列表项完全渲染（Transition 动画已经完成，这里主要是等待列表渲染）
  setTimeout(() => {
    memberListAreaRef.value?.scrollToTop()
  }, 50)
}

/** 移动端 Drawer 关闭 */
const handleDrawerClose = () => {
  emit('update:drawerVisible', false)
}
</script>

<template>
  <!-- 移动端：使用 el-drawer 包裹 -->
  <el-drawer
    v-if="isMobile"
    :model-value="drawerVisible"
    direction="btt"
    size="60%"
    :show-close="true"
    :with-header="true"
    :title="t('chat.members')"
    class="mobile-member-drawer"
    @close="handleDrawerClose"
  >
    <div class="right-aside-wrapper drawer-content">
      <ChatMemberList ref="memberListAreaRef" />
      <Transition name="right-aside-fade" @after-leave="handleOverlayHidden">
        <AppSpinner
          v-if="showRightSpinner"
          :absolute="true"
          :show-blobs="false"
          :show-text="true"
          :text="rightSpinnerText"
        />
      </Transition>
    </div>
  </el-drawer>

  <!-- 桌面端：普通渲染 -->
  <div v-else class="right-aside-wrapper">
    <!-- 成员列表区域始终渲染：这样 loading 遮罩的 backdrop-filter 才有"可模糊的内容"，不会看起来像纯白盖板 -->
    <ChatMemberList ref="memberListAreaRef" />
    <Transition name="right-aside-fade" @after-leave="handleOverlayHidden">
      <AppSpinner
        v-if="showRightSpinner"
        :absolute="true"
        :show-blobs="false"
        :show-text="true"
        :text="rightSpinnerText"
      />
    </Transition>
  </div>
</template>

<style scoped>
.right-aside-wrapper {
  height: 100%;
  position: relative;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-aside); /* 适配暗黑模式 */
  transition: background-color 0.3s ease;
  animation: fadeIn 0.25s ease;
}

/* Drawer 内容区 */
.drawer-content {
  height: 100%;
  animation: none;
}

.right-aside-fade-enter-active,
.right-aside-fade-leave-active { transition: opacity 0.18s ease; }
.right-aside-fade-enter-from,
.right-aside-fade-leave-to { opacity: 0; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
</style>

<style>
/* 移动端成员列表 Drawer 全局样式 */
.mobile-member-drawer.el-drawer {
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.mobile-member-drawer .el-drawer__header {
  padding: 16px 20px;
  margin-bottom: 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.mobile-member-drawer .el-drawer__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-900);
}

.mobile-member-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}
</style>
