<script setup lang="ts">
/**
 * 移动端好友管理页组件
 *
 * 功能：
 * - 显示好友列表和待处理的好友申请
 * - 添加新好友（仅正式用户可用）
 * - 访客用户锁定状态提示与升级引导
 * - 好友管理功能集成
 *
 * 路由：/chat/friends（移动端好友页）
 *
 * 依赖：
 * - useFriendStore: 好友状态管理
 * - useUserStore: 用户状态管理
 * - FriendList: 好友列表组件
 * - RequestList: 好友申请列表组件
 * - AddFriendDialog: 添加好友对话框
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import { useFriendStore } from '@/stores/friendStore'
import { useRouter } from 'vue-router'
import FriendList from '@/views/friend/FriendList.vue'
import RequestList from '@/views/friend/RequestList.vue'
import AddFriendDialog from '@/components/AddFriendDialog.vue'
import { ref, onMounted } from 'vue'
import { Lock, Plus } from '@element-plus/icons-vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const friendStore = useFriendStore()

const isGuest = computed(() => userStore.loginUserInfo?.userType === 'guest')
const addFriendDialogVisible = ref(false)

/**
 * 组件挂载时的初始化逻辑
 *
 * 功能：
 * - 仅当用户不是访客时加载好友数据
 * - 并行获取好友列表和待处理的好友申请
 * - 避免访客用户调用不需要的API
 */
onMounted(() => {
  if (!isGuest.value) {
    friendStore.fetchFriends()
    friendStore.fetchRequests()
  }
})

/**
 * 处理访客升级导航
 *
 * 功能：
 * - 当访客用户点击升级按钮时调用
 * - 跳转到设置页面的升级表单
 * - 传递查询参数标识升级流程
 */
const handleUpgrade = () => {
  router.push('/chat/settings?upgrade=true')
}
</script>

<template>
  <div class="mobile-friends-view">
    <div class="mobile-page-header">
      <h1 class="page-title">{{ t('mobile.tab_friends') }}</h1>
      <el-button
        v-if="!isGuest"
        type="primary"
        :icon="Plus"
        circle
        size="small"
        @click="addFriendDialogVisible = true"
      />
    </div>

    <div v-if="isGuest" class="guest-locked-state">
      <div class="locked-icon">
        <el-icon :size="48"><Lock /></el-icon>
      </div>
      <h2 class="locked-title">{{ t('mobile.friends_locked_title') }}</h2>
      <p class="locked-desc">{{ t('mobile.friends_locked_desc') }}</p>
      <el-button type="primary" size="large" @click="handleUpgrade">
        {{ t('mobile.upgrade_now') }}
      </el-button>
    </div>

    <div v-else class="friends-content">
      <RequestList />
      <FriendList />
    </div>

    <AddFriendDialog v-model:visible="addFriendDialogVisible" />
  </div>
</template>

<style scoped>
.mobile-friends-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  /* padding-bottom removed, layout handled by flex */
  /* padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom)); */
  box-sizing: border-box;
}

.mobile-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.guest-locked-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  text-align: center;
  min-height: 0;
}

.locked-icon {
  color: var(--text-400);
  margin-bottom: 16px;
}

.locked-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-700);
  margin: 0 0 8px;
}

.locked-desc {
  font-size: 14px;
  color: var(--text-500);
  margin: 0 0 24px;
  max-width: 280px;
}

.friends-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
  min-height: 0;
}
</style>
