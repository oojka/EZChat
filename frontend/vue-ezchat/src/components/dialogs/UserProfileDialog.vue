<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useUserStore } from '@/stores/userStore'
import { useFriendStore } from '@/stores/friendStore'
import { getUserInfoApi } from '@/api/User'
import { ElMessage, ElMessageBox } from 'element-plus'
import Avatar from '@/components/Avatar.vue'
import { useI18n } from 'vue-i18n'
import type { LoginUserInfo } from '@/type'

const props = defineProps<{
  uid: string
}>()

const visible = defineModel<boolean>('visible')

const { t } = useI18n()
const userStore = useUserStore()
const friendStore = useFriendStore()

const loading = ref(false)
const userInfo = ref<LoginUserInfo | null>(null)

const isMe = computed(() => props.uid === userStore.loginUserInfo?.uid)
const isFriend = computed(() => friendStore.friends.some(f => f.uid === props.uid))

const fetchUserInfo = async () => {
  loading.value = true
  try {
    const res = await getUserInfoApi(props.uid)
    if (res.code === 200) {
      userInfo.value = res.data
    }
  } catch {
    ElMessage.error(t('dialog.network_error'))
  } finally {
    loading.value = false
  }
}

watch(() => props.uid, () => {
  if (visible.value && props.uid) {
    fetchUserInfo()
  }
})

watch(visible, (val) => {
  if (val && props.uid) {
    fetchUserInfo()
  }
})

const handleAddFriend = async () => {
  try {
    await friendStore.sendRequest(props.uid)
    visible.value = false
  } catch {
    // Error handled in store
  }
}

const handleDeleteFriend = () => {
  ElMessageBox.confirm(
    t('friend.remove_confirm'),
    t('common.warning'),
    {
      confirmButtonText: t('friend.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    }
  ).then(async () => {
    await friendStore.removeFriend(props.uid)
    visible.value = false
  })
}

const handleChat = async () => {
  await friendStore.startChat(props.uid)
  visible.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('friend.profile')"
    width="360px"
    class="ez-dialog profile-dialog"
    destroy-on-close
    align-center
  >
    <div v-loading="loading" class="profile-content">
      <div v-if="userInfo" class="user-card">
        <div class="avatar-section">
          <Avatar 
            :image="userInfo.avatar" 
            :text="userInfo.nickname" 
            :size="80" 
            shape="circle"
          />
        </div>
        
        <div class="info-section">
          <h3 class="nickname">{{ userInfo.nickname }}</h3>
          <div class="uid">UID: {{ userInfo.uid }}</div>
          <div class="bio">{{ userInfo.bio || t('friend.no_bio') }}</div>
        </div>

        <div class="actions">
          <!-- Self -->
          <div v-if="isMe" class="self-tag">
            {{ t('chat.me') || 'Me' }}
          </div>

          <!-- Friend Actions -->
          <template v-else-if="isFriend">
            <el-button type="primary" class="flex-1" @click="handleChat">
              {{ t('chat.send') }}
            </el-button>
            <el-button type="danger" plain class="flex-1" @click="handleDeleteFriend">
              {{ t('friend.delete') }}
            </el-button>
          </template>

          <!-- Stranger Actions -->
          <template v-else>
            <el-button type="primary" class="w-full" @click="handleAddFriend">
              {{ t('friend.add_friend') }}
            </el-button>
          </template>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.profile-content {
  padding: 10px 0 20px;
}

.user-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 20px;
}

.avatar-section {
  position: relative;
}

.info-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.nickname {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-900);
  margin: 0;
}

.uid {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text-400);
  background: var(--bg-fill-1);
  padding: 2px 8px;
  border-radius: 10px;
  align-self: center;
}

.bio {
  font-size: 14px;
  color: var(--text-500);
  line-height: 1.5;
  padding: 0 20px;
}

.actions {
  display: flex;
  gap: 12px;
  width: 100%;
  padding: 0 20px;
  margin-top: 10px;
}

.self-tag {
  background: var(--primary-light-9);
  color: var(--primary);
  font-weight: 600;
  padding: 8px 0;
  width: 100%;
  border-radius: 8px;
  font-size: 14px;
}

.w-full {
  width: 100%;
}

.flex-1 {
  flex: 1;
}
</style>
