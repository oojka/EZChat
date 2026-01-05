/**
 * AlertDialog 国际化使用示例
 * 
 * 展示了如何使用国际化后的 AlertDialog 组件
 */

import { showAlertDialog } from './AlertDialog'

/**
 * 示例 1: 使用 i18n key 作为标题和消息
 */
export const showSuccessAlertWithI18n = () => {
  showAlertDialog({
    title: 'dialog.success', // i18n key
    message: 'dialog.operation_success', // i18n key
    type: 'success',
    onConfirm: () => {
      console.log('Success alert confirmed')
    }
  })
}

/**
 * 示例 2: 使用直接文本（非 i18n key）
 */
export const showWarningAlertWithDirectText = () => {
  showAlertDialog({
    title: '自定义警告标题', // 直接文本，不会被翻译
    message: '这是一个自定义警告消息', // 直接文本
    type: 'warning',
    confirmText: '我知道了', // 直接文本
    onConfirm: () => {
      console.log('Warning alert confirmed')
    }
  })
}

/**
 * 示例 3: 混合使用 i18n key 和直接文本
 */
export const showErrorAlertMixed = () => {
  showAlertDialog({
    title: 'dialog.error', // i18n key
    message: '网络连接失败，请检查您的网络设置', // 直接文本
    type: 'error',
    onConfirm: () => {
      console.log('Error alert confirmed')
    }
  })
}

/**
 * 示例 4: 不提供标题，使用默认标题（根据类型自动选择）
 */
export const showInfoAlertWithDefaultTitle = () => {
  showAlertDialog({
    // 不提供 title，将根据 type 自动选择默认标题
    message: 'dialog.data_saved', // i18n key
    type: 'info',
    onConfirm: () => {
      console.log('Info alert confirmed')
    }
  })
}

/**
 * 示例 5: 使用不同的弹窗类型
 */
export const showVariousAlertTypes = () => {
  // 成功类型
  showAlertDialog({
    title: 'dialog.success',
    message: 'dialog.operation_success',
    type: 'success'
  })

  // 警告类型
  showAlertDialog({
    title: 'dialog.warning',
    message: 'dialog.cannot_undo',
    type: 'warning'
  })

  // 错误类型
  showAlertDialog({
    title: 'dialog.error',
    message: 'dialog.operation_failed',
    type: 'error'
  })

  // 主要类型（primary）
  showAlertDialog({
    title: 'dialog.info',
    message: 'dialog.please_wait',
    type: 'primary'
  })

  // 危险类型（danger）
  showAlertDialog({
    title: 'dialog.error',
    message: 'dialog.permission_denied',
    type: 'danger'
  })
}

/**
 * 示例 6: 在实际业务场景中的使用
 */
export const showBusinessAlerts = () => {
  // 表单验证失败
  showAlertDialog({
    title: 'dialog.validation_error',
    message: 'validation.username_required',
    type: 'error'
  })

  // 网络错误
  showAlertDialog({
    title: 'dialog.network_error',
    message: 'api.network_error',
    type: 'error'
  })

  // 会话过期
  showAlertDialog({
    title: 'dialog.session_expired',
    message: 'auth.session_expired',
    type: 'warning'
  })

  // 数据保存成功
  showAlertDialog({
    title: 'dialog.success',
    message: 'dialog.data_saved',
    type: 'success'
  })
}

/**
 * 示例 7: 带回调函数的复杂场景
 */
export const showAlertWithComplexCallback = () => {
  showAlertDialog({
    title: 'dialog.are_you_sure',
    message: 'dialog.cannot_undo',
    type: 'warning',
    confirmText: 'dialog.confirm',
    onConfirm: () => {
      // 执行删除操作
      console.log('Deleting data...')
      
      // 删除成功后显示成功提示
      setTimeout(() => {
        showAlertDialog({
          title: 'dialog.success',
          message: 'dialog.data_deleted',
          type: 'success'
        })
      }, 1000)
    }
  })
}

/**
 * 使用说明：
 * 
 * 1. 基本使用：
 *    showAlertDialog({
 *      title: 'dialog.success', // i18n key 或直接文本
 *      message: 'dialog.operation_success', // i18n key 或直接文本
 *      type: 'success',
 *      onConfirm: () => { /* 回调函数 *\/ }
 *    })
 * 
 * 2. 智能翻译：
 *    - 如果传入的是 i18n key（如 'dialog.success'），会自动翻译
 *    - 如果传入的是直接文本，会原样显示
 *    - 如果不提供 title，会根据 type 自动选择默认标题
 * 
 * 3. 支持的类型：
 *    - 'success': 成功提示
 *    - 'warning': 警告提示
 *    - 'error': 错误提示
 *    - 'info': 信息提示
 *    - 'primary': 主要提示
 *    - 'danger': 危险提示
 * 
 * 4. 多语言支持：
 *    所有支持的语言都会自动应用对应的翻译
 */
