<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/appStore.ts'
import { useUserStore } from '@/stores/userStore.ts'
import { useGuestJoin } from '@/hooks/chat/join/useGuestJoin.ts'
import { useLoginJoin } from '@/hooks/chat/join/useLoginJoin.ts'
import { computed } from 'vue'

// ... (imports remain)
import useLogin from '@/hooks/useLogin.ts'
import AppLogo from '@/components/AppLogo.vue'
import SmartAvatar from '@/components/SmartAvatar.vue'
import PasswordInput from '@/components/PasswordInput.vue'
import { Moon, Sunny, User, Camera, Picture, ArrowRight, CircleCheckFilled } from '@element-plus/icons-vue'
import { showAlertDialog } from '@/components/dialogs/AlertDialog'

const { locale, t } = useI18n()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { validatedChatRoom } = storeToRefs(userStore)
const { isDark } = storeToRefs(appStore)
const { setFavicon, removeFavicon } = appStore

const currentLangCode = {
  en: 'EN',
  ja: 'JP',
  zh: 'CN',
  ko: 'KO',
  'zh-tw': 'TW',
}

// 1. 实例化模块
const guestJoinModule = useGuestJoin()
const loginJoinModule = useLoginJoin()

// 3. 解构解构 Guest 模块状态与方法
const {
  guestNickname,
  guestAvatar,
  handleGuestJoin,
  handleAvatarSuccess,
  defaultAvatarUrl,
  initDefaultAvatarUrl,
} = guestJoinModule

const {
  handleLoginAndJoin
} = loginJoinModule

// 合并 Loading 状态
const isLoading = computed(() => guestJoinModule.isLoading.value || loginJoinModule.isLoading.value)


// 登录表单状态（使用 useLogin hook）
const { loginForm, resetLoginForm: resetLoginFormFromHook } = useLogin()

// 模式切换（UI 逻辑）
const showLoginMode = ref(false)

// 页面内容是否准备好显示（只有验证通过后才显示）
const isPageReady = ref(false)

// 页面加载时初始化业务数据和 UI
onMounted(async () => {
  setFavicon()

  // 先显示 loading 遮蔽层
  appStore.loadingText = t('common.loading') || 'Loading...'
  appStore.showLoadingSpinner = true
  appStore.isAppLoading = true

  // 等待 DOM 更新，确保 loading 遮蔽层已渲染
  await nextTick()

  if (!validatedChatRoom.value) {
    // 验证信息已失效（可能是用户刷新了页面），提示用户重新验证
    // Alert 会在 loading 遮蔽层之上显示
    await showAlertDialog({
      message: t('join_page.validation_expired') || 'Validation information has expired, please verify again',
      type: 'info',
    })
    // 关闭 loading 后跳转（不显示页面内容）
    appStore.isAppLoading = false
    appStore.showLoadingSpinner = false
    appStore.loadingText = ''
    router.replace('/').catch(() => { })
    return
  }

  // 验证信息存在，允许显示页面内容
  isPageReady.value = true
  initDefaultAvatarUrl()
  // 关闭 loading 并继续初始化
  appStore.isAppLoading = false
  appStore.showLoadingSpinner = false
  appStore.loadingText = ''
})

onUnmounted(() => {
  removeFavicon()
})

// 处理加入（根据模式调用不同的函数）
const handleJoin = async () => {
  if (showLoginMode.value) {
    await handleLoginAndJoin(loginForm)
  } else {
    await handleGuestJoin()
  }
}

// 导航到注册页面
const handleGoToRegister = () => {
  router.push({ path: '/', query: { register: 'true' } })
}
</script>

<template>
  <div class="join-root" v-if="isPageReady">
    <div class="page-wrapper">
      <div class="bg-blobs">
        <div class="blob blob-1"></div>
        <div class="blob blob-2"></div>
        <div class="blob blob-3"></div>
      </div>

      <div class="index-actions">
        <el-dropdown trigger="click" @command="appStore.changeLanguage" placement="bottom-end">
          <button class="action-icon-btn glass-btn">
            <span class="lang-code-text">{{ currentLangCode[locale as keyof typeof currentLangCode] }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu class="ez-dropdown-menu">
              <el-dropdown-item command="ja">日本語</el-dropdown-item>
              <el-dropdown-item command="en">English</el-dropdown-item>
              <el-dropdown-item command="zh">简体中文</el-dropdown-item>
              <el-dropdown-item command="zh-tw">繁體中文</el-dropdown-item>
              <el-dropdown-item command="ko">한국어</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button class="action-icon-btn glass-btn" @click="appStore.toggleTheme">
          <div class="theme-toggle-inner" :class="{ 'is-dark': isDark }">
            <el-icon>
              <Sunny v-if="!isDark" />
              <Moon v-else />
            </el-icon>
          </div>
        </button>
      </div>

      <div class="content-wrapper">
        <div class="logo-container">
          <AppLogo :size="100" :clickable="false" />
        </div>

        <div class="join-card">

          <div class="left-section">
            <div class="glass-overlay"></div>

            <div class="info-content-wrapper">
              <div class="invitation-header">
                <div class="status-badge">
                  <el-icon class="status-icon">
                    <CircleCheckFilled />
                  </el-icon>
                  <span>{{ t('join_page.invitation_confirmed') }}</span>
                </div>

                <h3 class="welcome-title">
                  {{ t('join_page.welcome_to_chatroom') }}
                </h3>
                <p class="welcome-desc">
                  {{ t('join_page.welcome_description') }}
                </p>
              </div>

              <div class="room-card-preview">
                <div class="room-avatar-wrapper">
                  <SmartAvatar :thumb-url="validatedChatRoom?.avatar?.imageThumbUrl"
                    :url="validatedChatRoom?.avatar?.imageUrl" :text="validatedChatRoom?.chatName" :size="100"
                    shape="square" class="room-avatar" />
                </div>

                <div class="room-details">
                  <h2 class="room-name">{{ validatedChatRoom?.chatName }}</h2>
                  <span class="meta-badge">
                    <el-icon>
                      <User />
                    </el-icon> {{ validatedChatRoom?.memberCount }} {{ t('join_page.members') }}
                  </span>
                  <p class="room-code">{{ t('join_page.room_id_prefix') }} {{ validatedChatRoom?.chatCode }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="right-section">
            <!-- 模式切换文字链接（右上角） -->
            <div class="mode-toggle-link">
              <template v-if="!showLoginMode">
                <span class="text-muted">{{ t('join_page.have_account') }}</span>
                <a class="link-btn" @click="showLoginMode = true">{{ t('join_page.login') }}</a>
              </template>
              <template v-else>
                <span class="text-muted">{{ t('join_page.temporary_use') }}</span>
                <a class="link-btn" @click="showLoginMode = false">{{ t('join_page.guest_mode') }}</a>
              </template>
            </div>

            <Transition name="fade-slide" mode="out-in">

              <div v-if="!showLoginMode" key="guest" class="form-container">
                <div class="form-content">
                  <div class="form-header">
                    <h3>{{ t('join_page.guest_mode_title') }}</h3>
                    <p>{{ t('join_page.guest_mode_description') }}</p>
                  </div>

                  <div class="avatar-upload-area">
                    <el-upload class="avatar-uploader" action="/api/auth/register/upload" :show-file-list="false"
                      :on-success="handleAvatarSuccess">
                      <div class="avatar-wrapper"
                        :class="{ 'has-image': guestAvatar.imageThumbUrl || guestAvatar.imageUrl || guestAvatar.blobUrl }">
                        <img v-if="guestAvatar.imageThumbUrl || guestAvatar.imageUrl || guestAvatar.blobUrl"
                          :src="guestAvatar.imageThumbUrl || guestAvatar.imageUrl || guestAvatar.blobUrl"
                          class="avatar-img" />
                        <img v-else-if="defaultAvatarUrl" :src="defaultAvatarUrl" class="avatar-img" />

                        <div v-else class="placeholder-state">
                          <div class="icon-circle">
                            <el-icon>
                              <Picture />
                            </el-icon>
                          </div>
                          <span class="upload-text">{{ t('join_page.click_to_upload_avatar') }}</span>
                        </div>

                        <div class="hover-mask">
                          <el-icon>
                            <Camera />
                          </el-icon>
                        </div>
                      </div>
                    </el-upload>
                    <p class="text-muted">{{ t('join_page.click_to_upload_avatar') }}</p>
                  </div>

                  <div class="input-group">
                    <el-input v-model="guestNickname" :placeholder="t('join_page.enter_nickname')"
                      class="custom-input" />
                  </div>
                </div>

                <div class="form-footer">
                  <el-button :loading="isLoading" type="primary" class="action-btn" @click="handleJoin">
                    {{ t('join_page.join_chat') }}
                    <el-icon class="el-icon--right">
                      <ArrowRight />
                    </el-icon>
                  </el-button>
                </div>
              </div>

              <div v-else key="login" class="form-container">
                <div class="form-content">
                  <div class="form-header">
                    <h3>{{ t('join_page.welcome_back') }}</h3>
                    <p>{{ t('join_page.login_and_join_description') }}</p>
                  </div>

                  <div class="input-group vertical">
                    <el-input class="username-input" v-model="loginForm.username" :placeholder="t('join_page.username')"
                      size="large">
                      <template #prefix><el-icon>
                          <User />
                        </el-icon></template>
                    </el-input>

                    <PasswordInput class="password-input" v-model="loginForm.password"
                      :placeholder="t('join_page.password')" />
                  </div>
                </div>

                <div class="form-footer form-footer-login">
                  <el-button :loading="isLoading" type="primary" class="action-btn" @click="handleJoin">
                    {{ t('join_page.login_and_join') }}
                    <el-icon class="el-icon--right">
                      <ArrowRight />
                    </el-icon>
                  </el-button>

                  <div class="signup-link-wrapper">
                    <span class="signup-text">{{ t('join_page.no_account') }}</span>
                    <a class="signup-btn" @click="handleGoToRegister">{{ t('join_page.register') }}</a>
                  </div>
                </div>
              </div>
            </Transition>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* --- 全局变量与基础设置 --- */
.join-root {
  --glass-bg: rgba(255, 255, 255, 0.7);
  --glass-border: rgba(255, 255, 255, 0.5);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.05);
  --card-bg-left: linear-gradient(135deg, #fdfbfb 0%, #ebedee 100%);
  --text-main: #2c3e50;
  --text-sub: #606266;
  --primary-gradient: linear-gradient(135deg, #409eff 0%, #3b82f6 100%);
  --input-bg: #ffffff;
  --input-border: #e2e8f0;
  --accent-color: #409eff;
}

html.dark .join-root {
  --glass-bg: rgba(30, 30, 30, 0.6);
  --glass-border: rgba(255, 255, 255, 0.08);
  --glass-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
  --card-bg-left: linear-gradient(135deg, #1f1f1f 0%, #121212 100%);
  --text-main: #f3f4f6;
  --text-sub: #a1a1aa;
  --input-bg: #27272a;
  --input-border: #3f3f46;
}

.page-wrapper {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  perspective: 2000px;
  transition: background 0.8s var(--ease-out-expo);
}

html.dark .page-wrapper {
  background: var(--bg-page);
  background-image:
    radial-gradient(circle at 0% 0%, var(--primary-light) 0%, transparent 70%),
    radial-gradient(circle at 100% 100%, var(--primary-light) 0%, transparent 70%);
}

/* --- 背景动效 --- */
.bg-blobs {
  position: absolute;
  inset: -50%;
  z-index: 0;
  pointer-events: none;
  animation: global-rotate 150s linear infinite;
}

.blob {
  position: absolute;
  filter: blur(120px);
  opacity: 0.4;
  transition: all 1s var(--ease-out-expo);
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  animation: blob-breathe 20s ease-in-out infinite;
}

.blob-1 {
  background: #60a5fa;
  width: 600px;
  height: 600px;
  top: 25%;
  left: 25%;
}

.blob-2 {
  background: #93c5fd;
  width: 700px;
  height: 700px;
  bottom: 25%;
  right: 25%;
  animation-delay: -10s;
}

.blob-3 {
  background: #a5f3fc;
  width: 400px;
  height: 400px;
  top: 40%;
  left: 40%;
  opacity: 0.3;
  animation-delay: -5s;
}

:deep(.dark) .blob {
  filter: blur(180px);
}

:deep(.dark) .blob-1 {
  background: #080808;
  opacity: 0.02;
}

:deep(.dark) .blob-2 {
  background: #0a0a0a;
  opacity: 0.02;
}

:deep(.dark) .blob-3 {
  opacity: 0.01;
}

@keyframes global-rotate {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

@keyframes blob-breathe {

  0%,
  100% {
    transform: scale(1);
    opacity: 0.3;
  }

  50% {
    transform: scale(1.3);
    opacity: 0.6;
  }
}

/* --- 内容区域 --- */
.content-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  z-index: 2;
  padding: 30px 20px 20px;
}

.logo-container {
  margin-bottom: 24px;
  text-align: center;
}

/* --- 顶部按钮 --- */
.index-actions {
  position: absolute;
  top: 24px;
  right: 24px;
  z-index: 50;
  display: flex;
  gap: 12px;
}

.glass-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-main);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.9);
}

html.dark .glass-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

/* --- 主卡片容器 --- */
.join-card {
  position: relative;
  display: flex;
  width: 900px;
  min-height: 400px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  box-shadow: var(--glass-shadow);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  overflow: hidden;
  z-index: 10;
}

/* --- 左侧：信息展示区 --- */
.left-section {
  width: 65%;
  flex-shrink: 0;
  background: var(--card-bg-left);
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 20px;
  overflow: hidden;
  box-sizing: border-box;
}

/* 左侧装饰 */
.decoration-circle {
  position: absolute;
  width: 300px;
  height: 300px;
  border: 40px solid rgba(64, 158, 255, 0.03);
  border-radius: 50%;
  bottom: -100px;
  left: -100px;
}

.info-content-wrapper {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  max-width: 450px;
  margin: 0 auto;
  text-align: center;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

/* --- 上方区域设计：邀请函头部 --- */
.invitation-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  margin-top: 20px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 20px;
  font-size: 15px;
  font-weight: 700;
  color: #10b981;
  letter-spacing: 0.3px;
  margin-bottom: 32px;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.15);
}

.status-icon {
  font-size: 20px;
  color: #10b981;
}

.welcome-title {
  font-size: 30px;
  font-weight: 800;
  color: var(--text-main);
  margin: 0 0 16px 0;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, var(--text-main) 0%, var(--text-sub) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.3;
}

.welcome-desc {
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.8;
  max-width: 420px;
  margin: 0 auto;
  opacity: 0.9;
}

/* 房间小卡片 */
.room-card-preview {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
  max-width: 100%;
  margin: 0;
  box-sizing: border-box;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  /* 轻微阴影增加层次 */
  position: relative;
  margin-top: 32px;
  margin-bottom: 14px;
}

/* 模拟票据撕口效果 (可选) */
.room-card-preview::before,
.room-card-preview::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  background: var(--card-bg-left);
  /* 与背景色相同实现镂空感 */
  border-radius: 50%;
  transform: translateY(-50%);
  /* 只有背景色纯色时才有效，渐变背景下效果一般，此处仅作结构占位，可根据需求调整 */
  opacity: 0;
}

html.dark .room-card-preview {
  background: rgba(0, 0, 0, 0.2);
  border-color: rgba(255, 255, 255, 0.05);
}

.room-avatar-wrapper {
  flex-shrink: 0;
}

.room-avatar {
  border-radius: var(--radius-base);
  border: 1px solid var(--el-border-color-light);
  transition: transform 0.3s ease;
}

.room-card-preview:hover .room-avatar {
  transform: scale(1.05);
}

.room-details {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
  text-align: left;
  /* 强制左对齐 */
}

.room-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
  line-height: 1.4;
}

.meta-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-sub);
  background: rgba(0, 0, 0, 0.04);
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 600;
  white-space: nowrap;
  width: fit-content;
}

html.dark .meta-badge {
  background: rgba(255, 255, 255, 0.1);
}

.meta-badge .el-icon {
  font-size: 12px;
  color: #409eff;
}

.room-code {
  font-size: 13px;
  color: var(--text-sub);
  margin: 0;
  font-family: 'Monaco', 'Courier New', monospace;
  letter-spacing: 0.5px;
  opacity: 0.8;
}

/* --- 右侧：表单区 --- */
.right-section {
  width: 35%;
  flex-shrink: 0;
  background: var(--bg-glass);
  backdrop-filter: var(--blur-glass);
  -webkit-backdrop-filter: var(--blur-glass);
  border-left: 1px solid var(--border-glass);
  padding: 24px 24px 8px 24px;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: stretch;
  box-sizing: border-box;
  transition: all 0.3s ease;
  position: relative;
  min-height: 0;
}

html.dark .right-section {
  background: var(--bg-card);
  backdrop-filter: blur(24px) saturate(200%);
  -webkit-backdrop-filter: blur(24px) saturate(200%);
}

.form-container {
  width: 100%;
  max-width: 300px;
  /* 使用 flex: 1 填充可用空间，同时设置最小高度防止抖动 */
  flex: 1;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  /* 添加上边距，使表单内容独立于模式切换区域 */
  padding-top: 32px;
  padding-bottom: 0;
  box-sizing: border-box;
}

/* 表单内容区域 */
.form-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

/* 表单底部区域（固定） */
.form-footer {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 10px;
  margin-top: auto;
  margin-bottom: 16px;
  border-top: 1px solid var(--border-glass);
  box-sizing: border-box;
}

/* 登录模式的 footer 需要固定高度（包含注册引导链接） */
.form-footer-login {
  min-height: 70px;
}

.form-header {
  margin-top: 16px;
}

.form-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
  margin-bottom: 2px;
}

.form-header p {
  font-size: 10px;
  color: var(--text-sub);
  margin-bottom: 16px;
}

/* 头像上传样式优化 */
.avatar-upload-area {
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-bottom: 2px;
}

.avatar-upload-area .text-muted {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
  text-align: center;
}

.avatar-uploader {
  text-align: center;
  display: flex;
  justify-content: center;
}

.avatar-wrapper {
  width: 90px;
  height: 90px;
  border-radius: calc(90px * var(--avatar-border-radius-ratio));
  /* 27px (30%) */
  background: var(--input-bg);
  border: 2px solid var(--input-border);
  position: relative;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.avatar-wrapper:hover {
  border-color: #409eff;
  transform: scale(1.03);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.2);
}


.placeholder-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-sub);
}

.icon-circle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.04);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  transition: background 0.3s ease;
}

html.dark .icon-circle {
  background: rgba(255, 255, 255, 0.08);
}

.upload-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-sub);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hover-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 28px;
  opacity: 0;
  transition: opacity 0.3s ease;
  backdrop-filter: blur(2px);
}

.avatar-wrapper:hover .hover-mask {
  opacity: 1;
}

/* 输入框样式覆盖 */
.input-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 2px;
}

.input-group.vertical {
  gap: 12px;
}

.username-input {
  margin-bottom: 16px;
}

/* 统一输入框样式（与 RightCard.vue 和 main.css 保持一致） */
.input-group :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset;
  background-color: var(--bg-page);
  padding: 6px 16px;
  transition: all 0.3s;
}

.input-group :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) inset !important;
}

/* 按钮样式 */
.action-btn {
  height: 44px;
  border-radius: var(--radius-md);
  font-weight: 800;
  font-size: 16px;
  background: var(--primary);
  border: none;
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.25);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  width: 100%;
  letter-spacing: 0.3px;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.35);
}

.action-btn:active {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 模式切换文字链接（右上角） */
.mode-toggle-link {
  position: absolute;
  top: 24px;
  right: 24px;
  font-size: 11px;
  z-index: 10;
  /* 确保模式切换区域有足够的独立空间 */
  line-height: 1.5;
}

.text-muted {
  color: var(--text-500);
}

.link-btn {
  color: var(--primary);
  font-weight: 600;
  margin-left: 4px;
  cursor: pointer;
  transition: opacity 0.2s;
  text-decoration: none;
}

.link-btn:hover {
  text-decoration: underline;
  opacity: 0.8;
}

/* 注册引导区域 */
.signup-link-wrapper {
  margin-top: 0;
  text-align: center;
  font-size: 10px;
}

/* Footer 内的注册引导区域 */
.form-footer .signup-link-wrapper {
  margin-top: 0;
}

.signup-text {
  color: var(--text-500);
}

.signup-btn {
  color: var(--primary);
  font-weight: 700;
  cursor: pointer;
  margin-left: 4px;
  font-size: 12px;
  transition: all 0.2s;
  text-decoration: none;
}

.signup-btn:hover {
  text-decoration: underline;
  opacity: 0.8;
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
