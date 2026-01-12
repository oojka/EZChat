<script setup lang="ts">
/**
 * 添加好友对话框组件
 *
 * 功能：
 * - 通过 UID 搜索并添加好友
 * - 发送好友申请
 * - 加载状态反馈
 *
 * Props（v-model）：
 * - visible: 对话框可见状态
 *
 * 依赖：
 * - friendStore: 好友状态管理
 */
import { ref } from 'vue'
import { useFriendStore } from '@/stores/friendStore'
import { User } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const visible = defineModel<boolean>('visible')
const targetUid = ref('')
const loading = ref(false)
const friendStore = useFriendStore()

const handleAdd = async () => {
  if (!targetUid.value) return
  loading.value = true
  try {
    await friendStore.sendRequest(targetUid.value)
    visible.value = false
    targetUid.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('friend.add_friend')"
    width="400px"
    class="ez-dialog"
    destroy-on-close
  >
    <div class="add-friend-content">
      <el-input
        v-model="targetUid"
        :placeholder="t('friend.add_friend_placeholder')"
        @keyup.enter="handleAdd"
        size="large"
      >
        <template #prefix>
          <el-icon><User /></el-icon>
        </template>
      </el-input>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleAdd" :loading="loading">
          {{ t('friend.send_request') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.add-friend-content {
  padding: 10px 0;
}
</style>
