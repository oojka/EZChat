<script setup lang="ts">
import ChatMemberListArea from '../components/ChatMemberListArea.vue'
import AppSpinner from '@/components/AppSpinner.vue'
import { useAppStore } from '@/stores/appStore'
import { useMessageStore } from '@/stores/messageStore'
import { useRoomStore } from '@/stores/roomStore'
import { storeToRefs } from 'pinia'
import { computed, watch } from 'vue'

const appStore = useAppStore()
const messageStore = useMessageStore()
const roomStore = useRoomStore()
const { isAppInitializing } = storeToRefs(appStore)
const { chatViewIsLoading } = storeToRefs(messageStore)
const { currentRoomCode, isCurrentRoomMembersLoading } = storeToRefs(roomStore)
const { fetchRoomMembers } = roomStore

/**
 * 右侧成员列表的加载策略
 *
 * 业务目的：
 * - refresh 时避免右侧区域出现“骨架屏”（由你指定改为 AppSpinner）
 * - 等 chatView 开始拉取消息/房间信息后再显示真实列表
 */
const showRightSpinner = computed(() =>
  isAppInitializing.value || chatViewIsLoading.value || isCurrentRoomMembersLoading.value
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
</script>

<template>
  <div class="right-aside-wrapper">
    <!-- 成员列表区域始终渲染：这样 loading 遮罩的 backdrop-filter 才有“可模糊的内容”，不会看起来像纯白盖板 -->
    <ChatMemberListArea />
    <Transition name="right-aside-fade">
      <AppSpinner
        v-if="showRightSpinner"
        :absolute="true"
        :show-blobs="false"
        :show-text="true"
        text="LOADING..."
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

.right-aside-fade-enter-active,
.right-aside-fade-leave-active { transition: opacity 0.18s ease; }
.right-aside-fade-enter-from,
.right-aside-fade-leave-to { opacity: 0; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
</style>
