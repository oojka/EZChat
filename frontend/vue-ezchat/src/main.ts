import {createApp} from 'vue'
import {createPinia} from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css' // 引入暗黑模式变量
import '@/assets/main.css'
import '@/assets/styles/ez-dialog.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { setupGlobalErrorHandler } from '@/error/ErrorHandler.ts'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)
setupGlobalErrorHandler(app)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')

/**
 * 移除 index.html 的首屏遮罩（#boot-loading）
 *
 * 业务目的：
 * - 该遮罩不依赖 #app:empty，可避免 Vue mount 时出现“空窗帧闪烁”
 * - 采用淡出方式移除，确保与 App 内的全局 Loading 无缝衔接
 */
const boot = document.getElementById('boot-loading')
if (boot) {
  // 让浏览器至少绘制一帧再淡出，避免刚 mount 就立刻移除导致闪烁
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      boot.classList.add('is-hidden')
      window.setTimeout(() => boot.remove(), 300)
    })
  })
}
