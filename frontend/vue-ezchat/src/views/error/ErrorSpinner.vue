<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

// ==========================================
// Favicon 旋转动画工具函数
// ==========================================
const useFaviconSpinner = () => {
  let timer: number | null = null
  let canvas: HTMLCanvasElement | null = null
  let ctx: CanvasRenderingContext2D | null = null
  let angle = 0
  let faviconLink: HTMLLinkElement | null = null
  let isStopped = false

  const getLink = (): HTMLLinkElement | null => {
    if (isStopped) return null
    if (faviconLink) return faviconLink

    faviconLink = document.querySelector("link[rel*='icon']") as HTMLLinkElement
    if (!faviconLink) {
      faviconLink = document.createElement('link')
      faviconLink.rel = 'icon'
      document.head.appendChild(faviconLink)
    }
    return faviconLink
  }

  const drawFrame = () => {
    if (!canvas || !ctx || isStopped) return
    ctx.clearRect(0, 0, 32, 32)
    ctx.beginPath()
    ctx.lineWidth = 4
    ctx.strokeStyle = '#409eff' // 主题色
    ctx.lineCap = 'round'
    ctx.arc(16, 16, 12, angle, angle + 1.5 * Math.PI)
    ctx.stroke()

    const link = getLink()
    if (link) {
      link.href = canvas.toDataURL('image/png')
    }
    angle += 0.2
    if (angle > Math.PI * 2) angle = 0
  }

  const start = () => {
    isStopped = false
    const link = getLink()
    if (!link) return

    canvas = document.createElement('canvas')
    canvas.width = 32
    canvas.height = 32
    ctx = canvas.getContext('2d')
    if (!ctx) return

    if (timer) clearInterval(timer)
    timer = window.setInterval(drawFrame, 50)
  }

  const stop = () => {
    isStopped = true

    if (timer) {
      clearInterval(timer)
      timer = null
    }

    canvas = null
    ctx = null
    angle = 0

    // 错误页不应该显示 favicon，直接移除所有 favicon 链接
    const links = document.querySelectorAll("link[rel*='icon']")
    links.forEach((link) => {
      link.remove()
    })

    faviconLink = null
  }

  return { start, stop }
}

// ==========================================
// 组件业务逻辑
// ==========================================

const { start: startFaviconSpinner, stop: stopFaviconSpinner } = useFaviconSpinner()

onMounted(() => {
  const route = useRoute()
  if (route.query.code != '500') {
    document.title = 'ez-chat.oojka.com'
    setTimeout(() => {
      startFaviconSpinner()
    }, 100)
  }
})

onUnmounted(() => {
  stopFaviconSpinner()
})
</script>

<template>
  <!-- 全屏全白遮蔽（无转圈无文字） -->
  <div class="error-loading-overlay"></div>
</template>

<style scoped>
.error-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 99999;
  background-color: #ffffff;
}
</style>
