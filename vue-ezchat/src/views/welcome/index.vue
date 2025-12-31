<script setup lang="ts">
import {ChatDotRound, Plus, Promotion} from '@element-plus/icons-vue'
import {storeToRefs} from 'pinia'
import {useAppStore} from '@/stores/appStore.ts'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const appStore = useAppStore()
const { createRoomVisible } = storeToRefs(appStore)

const openCreateDialog = () => {
  createRoomVisible.value = true
}
</script>

<template>
  <div class="welcome-wrapper">
    <div class="bg-decoration">
      <div class="shape circle-1"></div>
      <div class="shape circle-2"></div>
    </div>

    <div class="welcome-content">
      <div class="icon-box">
        <el-icon class="welcome-icon"><ChatDotRound /></el-icon>
      </div>

      <h2 class="title">{{ t('welcome.title') }}</h2>
      <p class="subtitle">{{ t('welcome.subtitle') }}</p>

      <div class="actions">
        <el-button type="primary" class="action-btn create-btn" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          <span>{{ t('welcome.create_btn') }}</span>
        </el-button>

        <el-button class="action-btn join-btn">
          <el-icon><Promotion /></el-icon>
          <span>{{ t('welcome.join_btn') }}</span>
        </el-button>
      </div>

      <div class="features-grid">
        <div class="feature-card">
          <div class="f-icon">🚀</div>
          <div class="f-title">{{ t('welcome.f_fast_title') }}</div>
          <div class="f-desc">{{ t('welcome.f_fast_desc') }}</div>
        </div>
        <div class="feature-card">
          <div class="f-icon">🔒</div>
          <div class="f-title">{{ t('welcome.f_safe_title') }}</div>
          <div class="f-desc">{{ t('welcome.f_safe_desc') }}</div>
        </div>
        <div class="feature-card">
          <div class="f-icon">✨</div>
          <div class="f-title">{{ t('welcome.f_easy_title') }}</div>
          <div class="f-desc">{{ t('welcome.f_easy_desc') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome-wrapper {
  height: 100%; width: 100%; display: flex; justify-content: center; align-items: center;
  background-color: var(--bg-page); /* 适配暗黑模式背景 */
  position: relative; overflow: hidden; transition: background-color 0.3s ease;
}

.bg-decoration { position: absolute; inset: 0; pointer-events: none; }
.shape { position: absolute; border-radius: 50%; filter: blur(60px); opacity: 0.3; }
.circle-1 { width: 300px; height: 300px; background: var(--primary); top: -100px; right: -50px; }
.circle-2 { width: 250px; height: 250px; background: #d9ecff; bottom: -50px; left: -50px; }
:deep(.dark) .circle-2 { background: #1e293b; }

.welcome-content { position: relative; z-index: 1; max-width: 600px; padding: 40px; text-align: center; }

.icon-box {
  width: 100px; height: 100px; background: var(--bg-card); border-radius: 30px;
  display: flex; align-items: center; justify-content: center; margin: 0 auto 32px;
  box-shadow: var(--shadow-glass); transform: rotate(-5deg);
  border: 1px solid var(--el-border-color-light);
}

.welcome-icon { font-size: 50px; color: var(--primary); }

.title { font-size: 32px; font-weight: 900; color: var(--text-900); margin-bottom: 16px; letter-spacing: -0.5px; }
.subtitle { font-size: 16px; color: var(--text-500); line-height: 1.8; margin-bottom: 40px; }

.actions { display: flex; gap: 16px; justify-content: center; margin-bottom: 60px; }
.action-btn { height: 48px; padding: 0 28px; border-radius: 14px; font-weight: 800; font-size: 15px; display: flex; align-items: center; gap: 8px; transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1); }

.create-btn { box-shadow: 0 8px 20px rgba(64, 158, 255, 0.25); }
.join-btn { background: var(--bg-card); border: 1px solid var(--el-border-color-light); color: var(--text-500); }
.join-btn:hover { background: var(--bg-page); color: var(--text-900); }

.features-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.feature-card {
  background: var(--bg-glass); backdrop-filter: blur(10px); padding: 20px; border-radius: 20px;
  border: 1px solid var(--border-glass); transition: all 0.3s ease;
}
.feature-card:hover { background: var(--bg-card); transform: translateY(-5px); box-shadow: var(--shadow-glass); }

.f-icon { font-size: 24px; margin-bottom: 12px; }
.f-title { font-size: 14px; font-weight: 800; color: var(--text-900); margin-bottom: 4px; }
.f-desc { font-size: 12px; color: var(--text-400); }
</style>
