<script setup lang="ts">
/**
 * 局部骨架屏：适配暗黑模式扫光动效
 */
</script>

<template>
  <div class="skeleton-message-list">
    <div v-for="i in 5" :key="i" class="sk-group">
      <div class="sk-msg-row left">
        <div class="sk-avatar"></div>
        <div class="sk-content">
          <div class="sk-nickname"></div>
          <div class="sk-bubble" :class="'w-' + ((i % 3) + 1)"></div>
        </div>
      </div>
      <div class="sk-msg-row right">
        <div class="sk-avatar"></div>
        <div class="sk-content">
          <div class="sk-bubble" :class="'w-' + (((i + 1) % 4) + 1)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.skeleton-message-list {
  height: 100%;
  background-color: var(--bg-page);
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 18px;
  overflow: hidden;
  transition: background-color 0.4s ease;
  /* 轻微玻璃化模糊，降低清晰度提升质感 */
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.sk-group { display: flex; flex-direction: column; gap: 18px; margin-bottom: 18px; }
.sk-msg-row { display: flex; align-items: flex-start; gap: 12px; width: 100%; }
.sk-msg-row.right { flex-direction: row-reverse; }

.sk-avatar, .sk-bubble, .sk-nickname {
  /* 优化扫光：使用更细腻的渐变和更快的速度 */
  background: linear-gradient(
    90deg,
    var(--bg-card) 25%,
    var(--el-border-color-light) 50%,
    var(--bg-card) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 2s infinite linear;
  border: 1px solid var(--el-border-color-extra-light);
  filter: blur(0.4px) saturate(1.05);
}

.sk-avatar { width: 38px; height: 38px; border-radius: var(--radius-base); flex-shrink: 0; }
.sk-content { display: flex; flex-direction: column; gap: 4px; max-width: 75%; }
.sk-msg-row.right .sk-content { align-items: flex-end; }
.sk-nickname { width: 60px; height: 12px; border-radius: 4px; margin-bottom: 2px; }
.sk-bubble { height: 40px; border-radius: var(--radius-md); width: fit-content; }

/* 模拟真实消息的不同长度（参考 MessageItem 的实际宽度范围） */
.w-1 { width: 120px; }
.w-2 { width: 180px; }
.w-3 { width: 240px; }
.w-4 { width: 160px; }
</style>
