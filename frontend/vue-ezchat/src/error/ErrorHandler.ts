import type { App } from 'vue'

export function setupGlobalErrorHandler(app: App) {
  // 1️⃣ Vue 组件 / 生命周期 / render 中的错误
  app.config.errorHandler = (err, instance, info) => {
    console.error('[Vue Error]', err)
    console.error('Component:', instance)
    console.error('Info:', info)

    // TODO: 统一提示
    // ElMessage.error('页面发生错误')

    // TODO: 上报错误（Sentry / 后端）
  }

  // 2️⃣ JS 运行时错误
  window.onerror = function (message, source, lineno, colno, error) {
    console.error('[JS Error]', message, error)

    // return true 可阻止控制台报错
    return false
  }

  // 3️⃣ Promise 未捕获异常（非常重要）
  window.onunhandledrejection = function (event) {
    console.error('[Unhandled Promise]', event.reason)

    // event.preventDefault()
  }
}
