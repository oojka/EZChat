<script setup lang="ts">
import { Moon, Operation, Setting, Sunny, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/userStore.ts'
import { useAppStore } from '@/stores/appStore.ts'
import { storeToRefs } from 'pinia'
import { useWebsocketStore } from '@/stores/websocketStore.ts'
import AppLogo from '@/components/AppLogo.vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from '@/components/dialogs/confirmDialog.ts'
import { useI18n } from 'vue-i18n'

const { t, locale } = useI18n()
const userStore = useUserStore()
const appStore = useAppStore()
const { isDark } = storeToRefs(appStore)

const websocketStore = useWebsocketStore()
const { wsDisplayState } = storeToRefs(websocketStore)
const router = useRouter()

const handleLogout = () => {
  showConfirmDialog({
    title: 'common.confirm',
    message: 'auth.logout_confirm',
    confirmText: 'auth.logout',
    type: 'danger',
    onConfirm: () => {
      userStore.logout()
    }
  })
}

const currentLangCode = {
  en: 'EN', ja: 'JP', zh: 'CN', ko: 'KO', 'zh-tw': 'TW'
}
</script>

<template>
  <div class="header-container">
    <div class="header-left">
      <AppLogo :size="36" />
    </div>

    <div class="header-right">
      <!-- 1. WebSocket 状态 -->
      <el-tooltip :content="`${t('chat.ws_status')}: ${wsDisplayState.text}`" placement="bottom">
        <div class="ws-status-container">
          <span class="status-dot" :style="{ backgroundColor: wsDisplayState.color }"></span>
          <span class="status-text" :style="{ color: wsDisplayState.color }">{{ wsDisplayState.text }}</span>
        </div>
      </el-tooltip>

      <!-- 2. 语言切换 -->
      <el-dropdown trigger="click" @command="appStore.changeLanguage" placement="bottom-end"
        popper-class="ez-header-popper">
        <div class="action-icon-btn">
          <Transition name="el-fade-in-linear" mode="out-in">
            <span :key="locale" class="lang-code-text">{{ currentLangCode[locale as keyof typeof currentLangCode]
            }}</span>
          </Transition>
        </div>
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

      <!-- 3. 夜间模式切换 -->
      <div class="action-icon-btn" @click="appStore.toggleTheme">
        <div class="theme-toggle-inner" :class="{ 'is-dark': isDark }">
          <el-icon>
            <Sunny v-if="!isDark" />
            <Moon v-else />
          </el-icon>
        </div>
      </div>

      <!-- 4. 设置菜单 -->
      <el-dropdown trigger="click" placement="bottom-end" popper-class="ez-header-popper"
        style="border-radius: var(--radius-base);">
        <el-button class="setting-btn" :icon="Operation" plain />
        <template #dropdown>
          <el-dropdown-menu class="ez-dropdown-menu" style="border-radius: var(--radius-base);">
            <el-dropdown-item :icon="Setting" style="border-radius: var(--radius-base);">{{ t('common.settings')
              }}</el-dropdown-item>
            <el-dropdown-item divided :icon="SwitchButton" @click="handleLogout" class="danger-item"
              style="border-radius: var(--radius-base);">
              {{ t('auth.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<style scoped>
.header-container {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--primary-light);
  padding: 0 24px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--el-border-color-light);
  transition: all 0.4s var(--ease-out-expo);
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ws-status-container {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--bg-card);
  padding: 4px 12px;
  border-radius: var(--radius-base);
  border: 1px solid var(--el-border-color-light);
  transition: all 0.3s ease;
}

html.dark .ws-status-container {
  background-color: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.05);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: background-color 0.3s ease, box-shadow 0.3s ease;
}

html.dark .status-dot {
  box-shadow: 0 0 8px currentColor;
}

.status-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.action-icon-btn {
  cursor: pointer;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--bg-glass);
  transition: all 0.3s var(--ease-out-expo);
  backdrop-filter: blur(4px);
  color: var(--text-500);
  border: 1px solid var(--border-glass);
}

.action-icon-btn:hover {
  background-color: var(--bg-card);
  transform: translateY(-1px);
  color: var(--primary);
  border-color: var(--primary);
}

html.dark .action-icon-btn:hover {
  box-shadow: 0 0 12px rgba(77, 171, 255, 0.2);
}

.lang-code-text {
  font-size: 12px;
  font-weight: 900;
  font-family: 'Inter', sans-serif;
}

.theme-toggle-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.5s var(--ease-out-expo);
}

.theme-toggle-inner.is-dark {
  transform: rotate(360deg);
}

.setting-btn {
  border: 1px solid var(--border-glass);
  background: var(--bg-glass);
  color: var(--text-500);
  font-size: 18px;
  transition: all 0.4s var(--ease-out-expo);
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
  border-radius: var(--radius-sm);
  padding: 0;
}

.setting-btn:hover {
  background-color: var(--bg-card);
  color: var(--primary);
  transform: rotate(90deg);
  border-color: var(--primary);
}

html.dark .setting-btn:hover {
  box-shadow: 0 0 12px rgba(77, 171, 255, 0.2);
}
</style>
