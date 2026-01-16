<script setup lang="ts">
/**
 * 移动端入口页布局壳组件
 *
 * 功能：
 * - 提供移动端认证流程的统一布局框架
 * - 动态背景动画（浮动光球效果）
 * - 玻璃拟态卡片容器
 * - 键盘弹出时自动收起 Header
 *
 * 使用场景：
 * - MobileWelcomeView（欢迎页）
 * - MobileLoginView（登录页）
 * - MobileGuestJoinView（访客加入页）
 * - MobileRegisterView（注册页）
 *
 * 插槽：
 * - default: 主内容区域
 */
import { useKeyboardVisible } from '@/composables/useKeyboardVisible'
import AppLogo from '@/components/AppLogo.vue'

const { isKeyboardVisible } = useKeyboardVisible()
</script>

<template>
  <div class="mobile-entry-shell">
     <!-- 动画背景 -->
    <div class="ambient-bg">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
      <div class="noise-overlay"></div>
    </div>

     <!-- 头部（仅Logo，无文字） -->
    <div class="index-header" :class="{ 'is-hidden': isKeyboardVisible }">
      <div class="logo-area">
        <div class="logo-glow"></div>
        <AppLogo :size="72" />
      </div>
    </div>

     <!-- 主卡片 -->
    <div class="auth-card glass-panel" :class="{ 'expanded': isKeyboardVisible }">
      <div class="content-wrapper">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ================= CSS VARIABLES ================= */
.mobile-entry-shell {
  --primary-rgb: 59, 130, 246;
  --glass-bg: rgba(255, 255, 255, 0.75);
  --glass-border: rgba(255, 255, 255, 0.5);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.05);
  --primary-gradient: linear-gradient(135deg, var(--primary) 0%, #6366f1 100%);
  --text-main: #1f2937;
  --text-sub: #6b7280;
  --input-bg: rgba(255, 255, 255, 0.6);
  --input-focus-bg: #ffffff;
  --tab-bg: rgba(0, 0, 0, 0.05);
  --tab-active-bg: #ffffff;
  --tab-active-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  --orb-1: #c7d2fe;
  --orb-2: #fbcfe8;
  --orb-3: #e9d5ff;
}

html.dark .mobile-entry-shell {
  --glass-bg: rgba(20, 20, 25, 0.65);
  --glass-border: rgba(255, 255, 255, 0.08);
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  --primary-gradient: linear-gradient(135deg, var(--primary) 0%, #4338ca 100%);
  --text-main: #f3f4f6;
  --text-sub: #9ca3af;
  --input-bg: rgba(0, 0, 0, 0.2);
  --input-focus-bg: rgba(0, 0, 0, 0.4);
  --tab-bg: rgba(255, 255, 255, 0.05);
  --tab-active-bg: rgba(255, 255, 255, 0.1);
  --tab-active-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  --orb-1: #1e1b4b;
  --orb-2: #312e81;
  --orb-3: #4c1d95;
}

/* ================= LAYOUT ================= */
.mobile-entry-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  overflow: hidden; /* Prevent body scroll */
  position: relative;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  /* Safe area handling will be inside the children if needed */
}

/* ... */

/* ================= GLASS CARD ================= */
.auth-card {
  border-radius: 32px 32px 0 0;
  display: flex;
  flex-direction: column;
  flex: 1; /* Take remaining space */
  min-height: 0;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 2;
  overflow-y: auto; /* Scroll inside the card */
  /* Ensure padding for safe area is handled */
  padding-bottom: var(--safe-area-bottom);
}

/* ================= AMBIENT BACKGROUND ================= */
.ambient-bg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
  animation: float 20s infinite ease-in-out;
}

.orb-1 {
  top: -10%;
  right: -10%;
  width: 80vw;
  height: 80vw;
  background: var(--orb-1);
  animation-delay: 0s;
}

.orb-2 {
  bottom: 20%;
  left: -20%;
  width: 60vw;
  height: 60vw;
  background: var(--orb-2);
  animation-delay: -5s;
}

.orb-3 {
  top: 40%;
  right: -20%;
  width: 50vw;
  height: 50vw;
  background: var(--orb-3);
  animation-delay: -10s;
}

.noise-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)' opacity='0.04'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 1;
}

@keyframes float {
  0% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
  100% { transform: translate(0, 0) scale(1); }
}

/* ================= HEADER ================= */
.index-header {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 0 20px;
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 2;
  position: relative;
}

.index-header.is-hidden {
  padding: 10px 0;
  opacity: 0;
  height: 0;
  margin: 0;
  transform: translateY(-20px);
  pointer-events: none;
}

.logo-area {
  position: relative;
  margin-bottom: 8px;
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 120%;
  height: 120%;
  background: var(--primary);
  filter: blur(30px);
  opacity: 0.3;
  border-radius: 50%;
}

/* ================= GLASS CARD ================= */
.auth-card {
  border-radius: 32px 32px 0 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  z-index: 2;
}

.glass-panel {
  background: var(--glass-bg);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid var(--glass-border);
  box-shadow: var(--glass-shadow);
}

.auth-card.expanded {
  border-radius: 20px 20px 0 0;
}

.content-wrapper {
  padding: 24px 24px 32px;
}
</style>