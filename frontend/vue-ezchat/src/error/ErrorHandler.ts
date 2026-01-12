import type { App } from 'vue'
import { createAppError, isAppError, ErrorType, ErrorSeverity, type AppError } from './ErrorTypes'

/**
 * 错误上报函数（可扩展为 Sentry / 后端上报）
 * 
 * @param error 错误对象
 */
const reportError = (error: AppError) => {
  // TODO: 实现错误上报逻辑
  // 例如：发送到 Sentry、后端日志服务等
  console.error('[Error Report]', {
    type: error.type,
    severity: error.severity,
    message: error.message,
    component: error.component,
    action: error.action,
    timestamp: new Date(error.timestamp).toISOString(),
    originalError: error.originalError
  })
}

/**
 * 根据错误类型和严重级别显示用户提示
 * 
 * @param error 错误对象
 */
const showUserMessage = (error: AppError) => {
  // FATAL 错误：显示错误消息并可能需要跳转错误页
  if (error.severity === ErrorSeverity.FATAL) {
    // TODO: 可以跳转到错误页面
    return
  }

  // ERROR 错误：显示错误消息
  if (error.severity === ErrorSeverity.ERROR) {
    return
  }

  // WARNING 错误：显示警告消息
  if (error.severity === ErrorSeverity.WARNING) {
    return
  }

}

/**
 * 将未知错误转换为 AppError
 * 
 * @param error 未知错误
 * @param defaultType 默认错误类型
 * @param component 组件名
 * @param action 操作名
 * @returns AppError 对象
 */
const normalizeError = (
  error: unknown,
  defaultType: ErrorType = ErrorType.UNKNOWN,
  component?: string,
  action?: string
): AppError => {
  // 如果已经是 AppError，直接返回
  if (isAppError(error)) {
    return error
  }

  // 如果是 Error 对象，提取消息
  if (error instanceof Error) {
    return createAppError(
      defaultType,
      error.message || 'Unknown error',
      {
        component,
        action,
        originalError: error
      }
    )
  }

  // 其他类型错误，转换为字符串
  return createAppError(
    defaultType,
    String(error) || 'Unknown error',
    {
      component,
      action,
      originalError: error
    }
  )
}

/**
 * 设置全局错误处理器
 * 
 * 业务逻辑：
 * 1. 捕获 Vue 组件错误（组件渲染、生命周期、事件处理）
 * 2. 捕获 JS 运行时错误
 * 3. 捕获 Promise 未捕获异常
 * 4. 统一错误处理和用户提示
 * 5. 错误上报（可扩展）
 * 
 * @param app Vue 应用实例
 */
export function setupGlobalErrorHandler(app: App) {
  // 1️⃣ Vue 组件 / 生命周期 / render 中的错误
  app.config.errorHandler = (err, instance, info) => {
    // 将错误标准化为 AppError
    const appError = normalizeError(
      err,
      ErrorType.COMPONENT,
      instance?.$options?.name || instance?.$options?.__name || 'UnknownComponent',
      'render'
    )

    // 忽略无意义的 [object Object] 错误（通常来自组件事件处理）
    if (appError.message === '[object Object]') {
      return
    }

    // 记录错误信息
    console.error('[Vue Error]', {
      error: appError,
      component: instance,
      info
    })

    // 显示用户提示
    showUserMessage(appError)

    // 上报错误
    reportError(appError)
  }

  // 2️⃣ JS 运行时错误
  window.onerror = function (message, source, lineno, colno, error) {
    // 将错误标准化为 AppError
    const appError = normalizeError(
      error || message,
      ErrorType.UNKNOWN,
      'Global',
      'runtime'
    )

    // 记录错误信息
    console.error('[JS Runtime Error]', {
      error: appError,
      message,
      source,
      lineno,
      colno
    })

    // 显示用户提示（仅对严重错误）
    if (appError.severity === ErrorSeverity.FATAL || appError.severity === ErrorSeverity.ERROR) {
      showUserMessage(appError)
    }

    // 上报错误
    reportError(appError)

    // return true 可阻止控制台报错（但我们选择 false，保留控制台错误）
    return false
  }

  // 3️⃣ Promise 未捕获异常（非常重要）
  window.onunhandledrejection = function (event) {
    // 将错误标准化为 AppError
    const appError = normalizeError(
      event.reason,
      ErrorType.NETWORK, // Promise 错误通常是网络或异步操作相关
      'Global',
      'promise'
    )

    // 记录错误信息
    console.error('[Unhandled Promise Rejection]', {
      error: appError,
      reason: event.reason
    })

    // 显示用户提示（仅对严重错误）
    if (appError.severity === ErrorSeverity.FATAL || appError.severity === ErrorSeverity.ERROR) {
      showUserMessage(appError)
    }

    // 上报错误
    reportError(appError)

    // 可以选择阻止默认行为（但通常保留，以便调试）
    // event.preventDefault()
  }

  // 4️⃣ 拦截 console.warn，屏蔽 Element Plus 把验证错误对象直接打印出来的干扰信息
  const originalWarn = console.warn
  console.warn = (...args) => {
    if (args.length > 0 && typeof args[0] === 'object' && args[0] !== null) {
      const err = args[0]
      // 检查是否为 async-validator 的验证错误对象 (键为表单字段且值为数组)
      // 用户反馈的结构示例: {nickname: Array(1)}
      const commonFields = ['nickname', 'username', 'password', 'confirmPassword', 'passwordConfirm', 'avatar']
      const keys = Object.keys(err)

      // 如果对象的所有键都是我们要屏蔽的字段，且值都是数组，则屏蔽
      if (keys.length > 0 && keys.every(key => commonFields.includes(key) && Array.isArray(err[key]))) {
        return
      }
    }
    originalWarn.apply(console, args)
  }
}
