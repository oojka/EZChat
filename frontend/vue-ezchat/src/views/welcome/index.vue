<script setup lang="ts">
/**
 * 聊天室欢迎页组件
 *
 * 功能：
 * - 未选择聊天室时的欢迎引导页面
 * - 快速创建聊天室入口
 * - 快速加入聊天室入口
 * - 功能特性展示卡片
 *
 * 路由：/chat（作为默认子路由）
 *
 * 依赖：
 * - roomStore: 控制对话框可见性
 * - vue-i18n: 国际化
 */
import { ChatDotRound, Plus, Promotion } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useRoomStore } from '@/stores/roomStore'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const roomStore = useRoomStore()
const { createChatDialogVisible, joinChatDialogVisible } = storeToRefs(roomStore)

const openCreateDialog = () => {
  createChatDialogVisible.value = true
}

const openJoinDialog = () => {
  joinChatDialogVisible.value = true
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
        <el-icon class="welcome-icon">
          <ChatDotRound />
        </el-icon>
      </div>

      <h2 class="title">{{ t('welcome.title') }}</h2>
      <p class="subtitle">{{ t('welcome.subtitle') }}</p>

      <div class="actions">
        <el-button type="primary" class="action-btn create-btn" @click="openCreateDialog">
          <el-icon>
            <Plus />
          </el-icon>
          <span>{{ t('welcome.create_btn') }}</span>
        </el-button>

        <el-button class="action-btn join-btn" @click="openJoinDialog">
          <el-icon>
            <Promotion />
          </el-icon>
          <span>{{ t('welcome.join_btn') }}</span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Layout */
.welcome-wrapper {
  height: 100%;
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: var(--bg-page);
  position: relative;
  overflow: hidden;
  transition: background-color 0.3s ease;
}

/* Background decoration */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.3;
}

.circle-1 {
  width: 300px;
  height: 300px;
  background: var(--primary);
  top: -100px;
  right: -50px;
}

.circle-2 {
  width: 250px;
  height: 250px;
  background: #d9ecff;
  bottom: -50px;
  left: -50px;
}

html.dark .circle-2 {
  background: #1e293b;
}

/* Content area */
.welcome-content {
  position: relative;
  z-index: 1;
  max-width: 600px;
  padding: 40px;
  text-align: center;
}

.icon-box {
  width: 100px;
  height: 100px;
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 32px;
  box-shadow: var(--shadow-glass);
  transform: rotate(-5deg);
  border: 1px solid var(--el-border-color-light);
}

.welcome-icon {
  font-size: 50px;
  color: var(--primary);
}

.title {
  font-size: 32px;
  font-weight: 900;
  color: var(--text-900);
  margin-bottom: 16px;
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: 16px;
  color: var(--text-500);
  line-height: 1.8;
  margin-bottom: 40px;
}

/* Actions */
.actions {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 60px;
}

.action-btn {
  height: 48px;
  padding: 0 28px;
  border-radius: var(--radius-md);
  font-weight: 800;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.create-btn {
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.25);
}

.join-btn {
  background: var(--bg-card);
  border: 1px solid var(--el-border-color-light);
  color: var(--text-500);
}

.join-btn:hover {
  background: var(--bg-page);
  color: var(--text-900);
}

/* Features grid */
.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.feature-card {
  background: var(--bg-glass);
  backdrop-filter: blur(10px);
  padding: 20px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-glass);
  transition: all 0.3s ease;
}

.feature-card:hover {
  background: var(--bg-card);
  transform: translateY(-5px);
  box-shadow: var(--shadow-glass);
}

.f-icon {
  font-size: 24px;
  margin-bottom: 12px;
}

.f-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-900);
  margin-bottom: 4px;
}

.f-desc {
  font-size: 12px;
  color: var(--text-400);
}

/* Responsive breakpoints */
/* Tablet */
@media (max-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* Mobile */
@media (max-width: 768px) {
  .welcome-content {
    padding: 24px 16px;
  }

  .title {
    font-size: 24px;
  }

  .subtitle {
    font-size: 14px;
    margin-bottom: 24px;
  }

  .actions {
    flex-direction: column;
    margin-bottom: 32px;
  }

  .action-btn {
    width: 100%;
    justify-content: center;
  }

  .features-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .icon-box {
    width: 80px;
    height: 80px;
    margin-bottom: 24px;
  }

  .welcome-icon {
    font-size: 40px;
  }
}
</style>
