<script setup lang="ts">
import ChatMemberListArea from '../components/ChatMemberListArea.vue'
</script>

<template>
  <div class="right-aside-wrapper">
    <ChatMemberListArea />
  </div>
</template>

<style scoped>
.right-aside-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-aside); /* 适配暗黑模式 */
  transition: background-color 0.3s ease;
  animation: fadeIn 0.25s ease;
}

@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }
</style>
