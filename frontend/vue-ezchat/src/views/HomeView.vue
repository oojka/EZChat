<script setup lang="ts">
import { computed } from 'vue'
import { useIsMobile } from '@/composables/useIsMobile'
import DesktopIndexView from '@/views/index/index.vue'
import MobileWelcomeView from '@/views/mobile/entry/MobileWelcomeView.vue'
import { useAppStore } from '@/stores/appStore.ts'
import { onMounted, onUnmounted } from 'vue'

const appStore = useAppStore()
const { setFavicon, removeFavicon } = appStore
const { isMobile } = useIsMobile()

const showMobile = computed(() => isMobile.value)

onMounted(() => {
  setFavicon()
})

onUnmounted(() => {
  removeFavicon()
})
</script>

<template>
  <component :is="showMobile ? MobileWelcomeView : DesktopIndexView" />
</template>

<style scoped>
/* No additional styles needed - each component handles its own styling */
</style>