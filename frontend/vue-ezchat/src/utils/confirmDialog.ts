import {ElMessageBox} from 'element-plus'

export type DialogType = 'success' | 'warning' | 'info' | 'error' | 'primary' | 'danger'

interface ConfirmDialogOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: DialogType
  onConfirm: () => void
  onCancel?: () => void
}

/**
 * 显示自定义风格的确认弹窗
 */
export const showConfirmDialog = ({
  title = '確認',
  message,
  confirmText = '確認',
  cancelText = 'キャンセル',
  type = 'warning',
  onConfirm,
  onCancel,
}: ConfirmDialogOptions) => {
  // 映射 type 到 Element Plus 的按钮样式
  let confirmButtonClass = 'ez-dialog-btn'
  if (type === 'danger') {
    confirmButtonClass += ' el-button--danger'
  } else if (type === 'primary') {
    confirmButtonClass += ' el-button--primary'
  } else if (type === 'success') {
    confirmButtonClass += ' el-button--success'
  } else {
    confirmButtonClass += ' el-button--primary'
  }

  // 映射 type 到 ElMessageBox 的 type (icon)
  let messageBoxType: 'success' | 'warning' | 'info' | 'error' = 'warning'
  if (type === 'success') messageBoxType = 'success'
  if (type === 'error' || type === 'danger') messageBoxType = 'error'
  if (type === 'info') messageBoxType = 'info'
  if (type === 'primary') messageBoxType = 'info'

  ElMessageBox.confirm(message, title, {
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    type: messageBoxType,
    confirmButtonClass: confirmButtonClass,
    cancelButtonClass: 'ez-dialog-btn',
    customClass: 'ez-logout-dialog',
    center: false,
    showClose: false,
  })
    .then(() => {
      onConfirm()
    })
    .catch(() => {
      if (onCancel) {
        onCancel()
      }
    })
}
