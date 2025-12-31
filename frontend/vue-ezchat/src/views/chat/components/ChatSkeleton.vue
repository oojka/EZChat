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
          <div class="sk-bubble w-40"></div>
        </div>
      </div>
      <div class="sk-msg-row right">
        <div class="sk-avatar"></div>
        <div class="sk-content">
          <div class="sk-bubble w-30"></div>
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
}

.sk-avatar { width: 38px; height: 38px; border-radius: var(--radius-base); flex-shrink: 0; }
.sk-content { display: flex; flex-direction: column; gap: 4px; max-width: 75%; }
.sk-msg-row.right .sk-content { align-items: flex-end; }
.sk-nickname { width: 60px; height: 12px; border-radius: 4px; margin-bottom: 2px; }
.sk-bubble { height: 40px; border-radius: var(--radius-md); }
.w-30 { width: 30%; }
.w-40 { width: 40%; }
</style>
