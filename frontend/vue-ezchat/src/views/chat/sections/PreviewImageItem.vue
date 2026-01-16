<script setup lang="ts">
import { toRef } from 'vue'
import { useImageLoader } from '@/composables/useImageLoader'
import type { Image } from '@/type'

const props = defineProps<{ image: Image }>()

const { currentUrl, isError, handleError } = useImageLoader(toRef(props, 'image'))
</script>

<template>
  <img v-if="currentUrl && !isError" :src="currentUrl" @error="handleError" />
  <div v-else class="preview-fallback">?</div>
</template>

<style scoped>
img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  background: var(--bg-page);
}

.preview-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  color: var(--text-400);
  font-size: 14px;
  font-weight: 600;
}
</style>
