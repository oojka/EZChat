<script setup lang="ts">
import {ref} from 'vue'
import {storeToRefs} from 'pinia'
import {useAppStore} from '@/stores/appStore.ts'
import AppLogo from '@/components/AppLogo.vue'
import LeftCard from '@/views/index/components/LeftCard.vue'
import RightCard from '@/views/index/components/RightCard.vue'
import {useI18n} from 'vue-i18n'
import {Moon, Sunny} from '@element-plus/icons-vue'

const { locale, t } = useI18n()
const appStore = useAppStore()
const { isDark } = storeToRefs(appStore)

const handleLanguageChange = (lang: string) => {
  locale.value = lang
  localStorage.setItem('locale', lang)
}

const currentLangCode = {
  en: 'EN', ja: 'JP', zh: 'CN', ko: 'KO', 'zh-tw': 'TW'
}

type CardType = 'guest' | 'login' | null
const activeCard = ref<CardType>(null)
const isGuestFlipped = ref(false)
const isLoginFlipped = ref(false)

const toggleCard = (type: CardType) => {
  if (activeCard.value !== type) {
    activeCard.value = type
    isGuestFlipped.value = false
    isLoginFlipped.value = false
  }
}

const resetCards = () => {
  activeCard.value = null
  isGuestFlipped.value = false
  isLoginFlipped.value = false
}

const handleGuestFlip = () => {
  if (activeCard.value === 'guest') isGuestFlipped.value = true
  else {
    activeCard.value = 'guest'
    setTimeout(() => { if (activeCard.value === 'guest') isGuestFlipped.value = true }, 600)
  }
}
const handleGuestUnflip = () => { isGuestFlipped.value = false }

const handleLoginFlip = () => {
  if (activeCard.value === 'login') isLoginFlipped.value = true
  else {
    activeCard.value = 'login'
    setTimeout(() => { if (activeCard.value === 'login') isLoginFlipped.value = true }, 600)
  }
}
const handleLoginUnflip = () => { isLoginFlipped.value = false }
</script>

<template>
  <div class="index-root" @click="resetCards">
    <div class="page-wrapper">

      <!-- 右上角功能矩阵 -->
      <div class="index-actions">
        <el-dropdown trigger="click" @command="handleLanguageChange" placement="bottom-end">
          <div class="action-icon-btn">
            <span class="lang-code-text">{{ currentLangCode[locale as keyof typeof currentLangCode] }}</span>
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

        <div class="action-icon-btn" @click="appStore.toggleTheme">
          <div class="theme-toggle-inner" :class="{ 'is-dark': isDark }">
            <el-icon><Sunny v-if="!isDark" /><Moon v-else /></el-icon>
          </div>
        </div>
      </div>

      <!-- 动态背景 -->
      <div class="bg-blobs">
        <div class="blob blob-1"></div>
        <div class="blob blob-2"></div>
      </div>

      <!-- 核心内容包裹层：整体上移 12% -->
      <div class="content-shift-wrapper">
        <div class="mainVisual" :class="{ 'is-dimmed': activeCard !== null }">
          <div class="logo-container">
            <AppLogo :size="100" :clickable="false" />
          </div>
          <p class="app-subtitle">{{ t('index.subtitle') }}</p>
        </div>

        <div class="container" :class="{ 'has-active-card': activeCard !== null }">
          <div
            class="item guest-card flip-card"
            :class="{ 'is-active': activeCard === 'guest', 'is-inactive': activeCard === 'login', 'is-flipped': isGuestFlipped }"
            @click.stop="toggleCard('guest')"
          >
            <LeftCard :active="activeCard === 'guest'" :flipped="isGuestFlipped" @flip="handleGuestFlip" @unflip="handleGuestUnflip" />
          </div>

          <div
            class="item login-card flip-card"
            :class="{ 'is-active': activeCard === 'login', 'is-inactive': activeCard === 'guest', 'is-flipped': isLoginFlipped }"
            @click.stop="toggleCard('login')"
          >
            <RightCard :active="activeCard === 'login'" :flipped="isLoginFlipped" @flip="handleLoginFlip" @unflip="handleLoginUnflip" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.index-root { width: 100%; min-height: 100vh; user-select: none; }
.index-root :deep(input), .index-root :deep(textarea) { user-select: text; }

.page-wrapper {
  position: relative; min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  overflow: hidden; display: flex; flex-direction: column; justify-content: center; align-items: center; perspective: 2000px;
  transition: background 0.8s var(--ease-out-expo);
}

html.dark .page-wrapper {
  background: var(--bg-page);
  background-image:
    radial-gradient(circle at 0% 0%, var(--primary-light) 0%, transparent 70%),
    radial-gradient(circle at 100% 100%, var(--primary-light) 0%, transparent 70%);
}

.index-actions { position: absolute; top: 24px; right: 24px; z-index: 100; display: flex; gap: 12px; align-items: center; }

.action-icon-btn {
  cursor: pointer; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center;
  border-radius: 12px; background: var(--bg-glass);
  backdrop-filter: var(--blur-glass); -webkit-backdrop-filter: var(--blur-glass);
  transition: all 0.4s var(--ease-out-expo);
  color: var(--text-700); border: 1px solid var(--border-glass);
  outline: none;
}
.action-icon-btn:hover {
  background-color: var(--bg-card); transform: translateY(-1px);
  color: var(--primary); border-color: var(--primary);
  box-shadow: var(--shadow-glass);
}

.lang-code-text { font-size: 12px; font-weight: 900; font-family: 'Inter', sans-serif; }
.theme-toggle-inner { display: flex; align-items: center; justify-content: center; transition: transform 0.5s var(--ease-out-expo); }
.theme-toggle-inner.is-dark { transform: rotate(360deg); }

.bg-blobs { position: absolute; inset: -50%; z-index: 0; pointer-events: none; animation: global-rotate 150s linear infinite; }
.blob {
  position: absolute; filter: blur(120px); opacity: 0.4;
  transition: all 1s var(--ease-out-expo);
  border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%;
  animation: blob-breathe 20s ease-in-out infinite;
}
.blob-1 { background: #60a5fa; width: 600px; height: 600px; top: 25%; left: 25%; }
.blob-2 { background: #93c5fd; width: 700px; height: 700px; bottom: 25%; right: 25%; animation-delay: -10s; }

:deep(.dark) .blob { filter: blur(180px); }
:deep(.dark) .blob-1 { background: #080808; opacity: 0.02; }
:deep(.dark) .blob-2 { background: #0a0a0a; opacity: 0.02; }

@keyframes global-rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
@keyframes blob-breathe {
  0%, 100% { transform: scale(1); border-radius: 40% 60% 70% 30% / 40% 50% 60% 50%; opacity: 0.3; }
  50% { transform: scale(1.5); border-radius: 60% 40% 30% / 70% 50% 60% 40%; opacity: 0.7; }
}

/* --- 核心修改：整体上移 12% --- */
.content-shift-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translateY(-12%); /* 整体上移 */
  transition: transform 0.8s var(--ease-out-expo);
}

.mainVisual { transition: all 0.8s var(--ease-out-expo); text-align: center; }
.mainVisual.is-dimmed { opacity: 0.05; transform: scale(0.8) translateY(-50px); filter: blur(12px); }
.app-subtitle { font-size: 18px; color: var(--text-500); letter-spacing: 0.1em; font-weight: 600; margin-top: 20px; transition: all 0.6s var(--ease-out-expo); }
html.dark .app-subtitle { color: #5f6368; text-shadow: 0 0 20px rgba(255, 255, 255, 0.05); }

.container { position: relative; z-index: 2; max-width: 1000px; width: 100%; display: flex; justify-content: center; gap: 32px; min-height: 450px; margin-top: 25px; transition: all 0.8s var(--ease-out-expo); }

.item {
  width: 460px; height: 420px; transition: all 0.8s var(--ease-out-expo);
  cursor: pointer; transform-style: preserve-3d; outline: none; border-radius: var(--radius-xl); -webkit-tap-highlight-color: transparent;
  background: transparent !important; box-shadow: none !important;
}

.item.is-active { z-index: 10; transform: scale(1.05); cursor: default; }
.guest-card.is-active { transform: translateX(calc(50% + 16px)) scale(1.05); }
.login-card.is-active { transform: translateX(calc(-50% - 16px)) scale(1.05); }
.item.is-inactive { opacity: 0.15; filter: blur(12px) grayscale(0.5); pointer-events: none; transform: scale(0.9); }
html.dark .item.is-inactive { opacity: 0.08; filter: blur(20px) grayscale(1); }

@media (max-width: 768px) { .container { flex-direction: column; align-items: center; } .item { width: 100%; max-width: 450px; } .item.is-active { transform: scale(1.05); } .guest-card.is-active, .login-card.is-active { transform: scale(1.05); } }
</style>
